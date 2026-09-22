package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.repository.PatientInsuranceBenefitRuleRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InsuranceBenefitRuleServiceTest {

    @Mock
    private PatientInsuranceBenefitRuleRepository benefitRuleRepository;

    @Mock
    private WaseelCoverageExtractionService coverageExtractionService;

    @Mock
    private WaseelEligibilityRequestRepository waseelEligibilityRequestRepository;

    @Mock
    private InsuranceBenefitRuleMatcher benefitRuleMatcher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PayorClient payorClient;

    @InjectMocks
    private InsuranceBenefitRuleService insuranceBenefitRuleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void isLatestCoverageInForce_nullInsurance_isFalse() {
        assertThat(insuranceBenefitRuleService.isLatestCoverageInForce(null)).isFalse();
    }

    @Test
    void isLatestCoverageInForce_nonWaseel_usesPatientExpirationDate() {
        PatientInsurance insurance = insurance(payor(false), LocalDate.now().plusDays(30));

        assertThat(insuranceBenefitRuleService.isLatestCoverageInForce(insurance)).isTrue();
        verify(waseelEligibilityRequestRepository, never())
                .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        anyLong(),
                        anyLong(),
                        anyString()
                );
        verify(coverageExtractionService, never()).isCoverageInForce(anyString());
    }

    @Test
    void isLatestCoverageInForce_nonWaseel_todayExpiration_isInForce() {
        PatientInsurance insurance = insurance(payor(false), LocalDate.now());

        assertThat(insuranceBenefitRuleService.isLatestCoverageInForce(insurance)).isTrue();
    }

    @Test
    void isLatestCoverageInForce_nonWaseel_expired_isNotInForce() {
        PatientInsurance insurance = insurance(payor(false), LocalDate.now().minusDays(1));

        assertThat(insuranceBenefitRuleService.isLatestCoverageInForce(insurance)).isFalse();
        verify(coverageExtractionService, never()).isCoverageInForce(anyString());
    }

    @Test
    void isLatestCoverageInForce_nonWaseel_missingExpiration_isNotInForce() {
        PatientInsurance insurance = insurance(payor(false), null);

        assertThat(insuranceBenefitRuleService.isLatestCoverageInForce(insurance)).isFalse();
    }

    @Test
    void isLatestCoverageInForce_waseel_keepsEligibilityResponseCheck() {
        PatientInsurance insurance = insurance(payor(true), LocalDate.now().plusDays(30));
        WaseelEligibilityRequest eligibility = WaseelEligibilityRequest.builder()
                .id(99L)
                .responseJson("{\"inforce\":true}")
                .build();

        when(waseelEligibilityRequestRepository
                .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        10L,
                        5L,
                        "SUCCESS"
                ))
                .thenReturn(Optional.of(eligibility));
        when(coverageExtractionService.isCoverageInForce(eligibility.getResponseJson()))
                .thenReturn(true);

        assertThat(insuranceBenefitRuleService.isLatestCoverageInForce(insurance)).isTrue();
        verify(coverageExtractionService).isCoverageInForce(eligibility.getResponseJson());
    }

    @Test
    void isLatestCoverageInForce_waseelWithoutEligibility_isNotInForce() {
        PatientInsurance insurance = insurance(payor(true), LocalDate.now().plusDays(30));

        when(waseelEligibilityRequestRepository
                .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        anyLong(),
                        anyLong(),
                        anyString()
                ))
                .thenReturn(Optional.empty());
        when(waseelEligibilityRequestRepository
                .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        anyLong(),
                        anyString()
                ))
                .thenReturn(Optional.empty());

        assertThat(insuranceBenefitRuleService.isLatestCoverageInForce(insurance)).isFalse();
        verify(coverageExtractionService, never()).isCoverageInForce(anyString());
    }

    @Test
    void isLatestCoverageInForce_payorLookupFailure_keepsWaseelEligibilityPath() {
        PatientInsurance insurance = insurance(null, LocalDate.now().plusDays(30));
        when(payorClient.getPayorById(7L)).thenThrow(feignException());
        when(waseelEligibilityRequestRepository
                .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        anyLong(),
                        anyLong(),
                        anyString()
                ))
                .thenReturn(Optional.empty());
        when(waseelEligibilityRequestRepository
                .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        anyLong(),
                        anyString()
                ))
                .thenReturn(Optional.empty());

        assertThat(insuranceBenefitRuleService.isLatestCoverageInForce(insurance)).isFalse();
        verify(coverageExtractionService, never()).isCoverageInForce(anyString());
    }

    private PatientInsurance insurance(PayorDTO payor, LocalDate expirationDate) {
        if (payor != null) {
            when(payorClient.getPayorById(7L)).thenReturn(payor);
        }

        Patient patient = new Patient();
        patient.setId(10L);

        return PatientInsurance.builder()
                .id(5L)
                .patient(patient)
                .payorId(7L)
                .expirationDate(expirationDate)
                .build();
    }

    private PayorDTO payor(boolean waseelEnabled) {
        return new PayorDTO(
                7L,
                "PAYOR",
                "Approval Coverage Co.",
                null,
                null,
                null,
                waseelEnabled,
                null,
                null,
                true
        );
    }

    private FeignException feignException() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/api/setup/payor/7",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        return FeignException.errorStatus(
                "getPayorById",
                feign.Response.builder()
                        .status(500)
                        .reason("error")
                        .request(request)
                        .headers(Collections.emptyMap())
                        .build()
        );
    }
}

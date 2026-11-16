package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.client.setup.dto.TpaResolutionDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityCheckRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApLovMapperService;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.service.PatientActivityService;
import com.dazzle.asklepios.service.helper.PayorHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaseelEligibilityCheckServiceTpaTest {

    private static final String PAYER = "7001544472";
    private static final String POLICY = "48095070";
    private static final String NEXTCARE = "7001580856";

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientInsuranceRepository patientInsuranceRepository;
    @Mock
    private WaseelEligibilityRequestRepository eligibilityLogRepository;
    @Mock
    private WaseelEligibilityService waseelEligibilityService;
    @Mock
    private WaseelApiProperties properties;
    @Mock
    private ApLovMapperService apLovMapperService;
    @Mock
    private EligibilityPatientInsuranceSyncService syncService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PayorHelper payorHelper;
    @Mock
    private PatientActivityService patientActivityService;

    private WaseelEligibilityCheckService service;
    private PatientInsurance insurance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new WaseelEligibilityCheckService(
                patientRepository,
                patientInsuranceRepository,
                eligibilityLogRepository,
                waseelEligibilityService,
                properties,
                new ObjectMapper(),
                apLovMapperService,
                syncService,
                eventPublisher,
                payorHelper,
                patientActivityService
        );

        Patient patient = new Patient();
        patient.setId(1L);
        patient.setDocumentId("1093772497");
        patient.setFirstName("Test");
        patient.setLastName("Patient");
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        patient.setSexAtBirth(Gender.values()[0]);

        insurance = PatientInsurance.builder()
                .id(2L)
                .patient(patient)
                .payerNphiesId(PAYER)
                .policyNumber(POLICY)
                .memberCardId("001093772497001")
                .expirationDate(LocalDate.now().plusYears(1))
                .build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(patientInsuranceRepository.findById(2L)).thenReturn(Optional.of(insurance));
        when(eligibilityLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(waseelEligibilityService.requestEligibility(any())).thenThrow(new ResourceAccessException("offline"));
    }

    @Test
    void tpaManagedPolicy_goesToTheTpaAndKeepsThePayer() {
        when(payorHelper.resolveTpa(PAYER, POLICY, null))
                .thenReturn(new TpaResolutionDTO("RESOLVED", NEXTCARE, "Nextcare"));

        EligibilityRequest sent = checkAndCaptureRequest();

        assertThat(sent.destinationId()).isEqualTo(NEXTCARE);
        assertThat(sent.insurancePlan().tpaNphiesId()).isEqualTo(NEXTCARE);
        assertThat(sent.insurancePlan().payerNphiesId()).isEqualTo(PAYER);
        assertThat(insurance.getTpaNphiesId()).isEqualTo(NEXTCARE);
        assertThat(insurance.getTpaName()).isEqualTo("Nextcare");
    }

    @Test
    void directPayer_goesToThePayer() {
        when(payorHelper.resolveTpa(PAYER, POLICY, null))
                .thenReturn(new TpaResolutionDTO("NO_TPA", null, null));

        EligibilityRequest sent = checkAndCaptureRequest();

        assertThat(sent.destinationId()).isEqualTo(PAYER);
        assertThat(sent.insurancePlan().tpaNphiesId()).isNull();
    }

    @Test
    void storedTpaWithName_isUsedWithoutResolvingAgain() {
        insurance.setTpaNphiesId(NEXTCARE);
        insurance.setTpaName("Nextcare");

        EligibilityRequest sent = checkAndCaptureRequest();

        assertThat(sent.destinationId()).isEqualTo(NEXTCARE);
        verify(payorHelper, never()).resolveTpa(anyString(), anyString(), any());
    }

    @Test
    void noTpaMarkerStoredOnInsurance_isNeverSentAsDestination() {
        insurance.setTpaNphiesId("-1");
        when(payorHelper.resolveTpa(eq(PAYER), eq(POLICY), isNull()))
                .thenReturn(new TpaResolutionDTO("NO_TPA", null, null));

        EligibilityRequest sent = checkAndCaptureRequest();

        assertThat(sent.destinationId()).isEqualTo(PAYER);
    }

    @Test
    void severalTpasWithoutAChoice_stopBeforeCallingWaseel() {
        when(payorHelper.resolveTpa(PAYER, POLICY, null))
                .thenReturn(new TpaResolutionDTO(TpaResolutionDTO.SELECTION_REQUIRED, null, null));

        assertThatThrownBy(this::check)
                .isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("more than one TPA");

        verify(waseelEligibilityService, never()).requestEligibility(any());
    }

    @Test
    void tpaWithoutNphiesId_stopsBeforeCallingWaseel() {
        when(payorHelper.resolveTpa(PAYER, POLICY, null))
                .thenReturn(new TpaResolutionDTO(TpaResolutionDTO.NPHIES_ID_MISSING, null, "Mednet"));

        assertThatThrownBy(this::check)
                .isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Mednet");

        verify(waseelEligibilityService, never()).requestEligibility(any());
    }

    private EligibilityRequest checkAndCaptureRequest() {
        assertThatThrownBy(this::check).isInstanceOf(BadRequestAlertException.class);

        ArgumentCaptor<EligibilityRequest> sent = ArgumentCaptor.forClass(EligibilityRequest.class);
        verify(waseelEligibilityService).requestEligibility(sent.capture());
        return sent.getValue();
    }

    private void check() {
        service.checkEligibility(new EligibilityCheckRequest(
                1L, 2L, null, LocalDate.now(), null, true, false, true, false, false
        ));
    }
}

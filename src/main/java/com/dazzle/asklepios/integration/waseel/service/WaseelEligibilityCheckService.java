package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.CoverageClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityBeneficiaryDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityCheckRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityCheckResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityInsurancePlanDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WaseelEligibilityCheckService {

    private final PatientRepository patientRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final WaseelEligibilityRequestRepository eligibilityLogRepository;
    private final WaseelEligibilityService waseelEligibilityService;
    private final WaseelApiProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public EligibilityCheckResponse checkEligibility(EligibilityCheckRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Eligibility check request is required");
        }

        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + request.patientId()));

        PatientInsurance insurance = patientInsuranceRepository.findById(request.patientInsuranceId())
                .orElseThrow(() -> new IllegalArgumentException("Patient insurance not found: " + request.patientInsuranceId()));

        if (insurance.getPatient() == null || !insurance.getPatient().getId().equals(patient.getId())) {
            throw new IllegalArgumentException("Patient insurance does not belong to selected patient");
        }

        LocalDate serviceDate = resolveServiceDate(request.serviceDate());

        EligibilityRequest waseelRequest = buildWaseelEligibilityRequest(
                patient,
                insurance,
                request,
                serviceDate
        );

        WaseelEligibilityRequest log = WaseelEligibilityRequest.builder()
                .patientId(patient.getId())
                .patientInsuranceId(insurance.getId())
                .payorId(insurance.getPayorId())
                .planId(insurance.getPlanId())
                .providerId(properties.providerId())
                .destinationId(request.destinationId())
                .serviceDate(serviceDate)
                .benefits(Boolean.TRUE.equals(request.benefits()))
                .discovery(Boolean.TRUE.equals(request.discovery()))
                .validation(Boolean.TRUE.equals(request.validation()))
                .transfer(Boolean.TRUE.equals(request.transfer()))
                .emergency(Boolean.TRUE.equals(request.emergency()))
                .requestStatus("PENDING")
                .requestedAt(Instant.now())
                .requestJson(toJson(waseelRequest))
                .build();

        log = eligibilityLogRepository.save(log);

        try {
            EligibilityResponse response = waseelEligibilityService.requestEligibility(waseelRequest);

            log.setApiStatus(response == null ? null : response.status());
            log.setStatusCode(response == null ? null : response.outcome());
            log.setMessage(response == null ? null : response.disposition());
            log.setEligibilityResponseId(response == null ? null : response.nphiesResponseId());
            log.setEligibilityResponseUrl(response == null ? null : response.eligibilityIdentifierUrl());
            log.setRequestStatus("SUCCESS");
            log.setRespondedAt(Instant.now());
            log.setResponseJson(toJson(response));

            WaseelEligibilityRequest saved = eligibilityLogRepository.save(log);

            return toResult(saved);

        } catch (Exception e) {
            log.setRequestStatus("FAILED");
            log.setRespondedAt(Instant.now());
            log.setMessage(e.getMessage());
            eligibilityLogRepository.save(log);
            throw e;
        }
    }

    private EligibilityRequest buildWaseelEligibilityRequest(
            Patient patient,
            PatientInsurance insurance,
            EligibilityCheckRequest request,
            LocalDate serviceDate
    ) {
        EligibilityInsurancePlanDTO insurancePlan = buildInsurancePlan(insurance);

        EligibilityBeneficiaryDTO beneficiary = new EligibilityBeneficiaryDTO(
                patient.getId(),
                buildFullName(patient),
                patient.getDocumentId(),
                null,
                clean(patient.getFirstName()),
                clean(patient.getSecondName()),
                clean(patient.getThirdName()),
                clean(patient.getLastName()),
                buildFullName(patient),
                patient.getDateOfBirth() == null ? null : patient.getDateOfBirth().toString(),
                patient.getSexAtBirth() == null ? null : patient.getSexAtBirth().name(),
                clean(patient.getNationality()),
                clean(patient.getPrimaryMobileNumber()),
                clean(patient.getEmail()),
                clean(patient.getEmergencyContactPhone()),
                null,
                null,
                null,
                null,
                clean(patient.getMaritalStatus()),
                null,
                null,
                clean(patient.getNativeLanguage()),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(insurancePlan),
                false
        );

        return new EligibilityRequest(
                false,
                beneficiary,
                null,
                insurancePlan,
                serviceDate.toString(),
                null,
                Boolean.TRUE.equals(request.benefits()),
                Boolean.TRUE.equals(request.discovery()),
                Boolean.TRUE.equals(request.validation()),
                Boolean.TRUE.equals(request.transfer()),
                Boolean.TRUE.equals(request.emergency()),
                false,
                java.util.Map.of(),
                clean(request.destinationId())
        );
    }

    private EligibilityInsurancePlanDTO buildInsurancePlan(PatientInsurance insurance) {
        return new EligibilityInsurancePlanDTO(
                insurance.getPlanId() == null ? null : insurance.getPlanId().toString(),
                insurance.getPayorId() == null ? null : insurance.getPayorId().toString(),
                null,
                clean(insurance.getMemberCardId()),
                clean(insurance.getPolicyNumber()),
                clean(insurance.getPayerNphiesId()),
                "-1",
                insurance.getExpirationDate() == null ? null : insurance.getExpirationDate().toString(),
                clean(insurance.getRelationWithSubscriber()),
                clean(insurance.getCoverageType()),
                toInteger(insurance.getPatientShare()),
                toInteger(insurance.getMaxLimit()),
                buildCoverageClassList(insurance),
                clean(insurance.getPolicyHolderName()),
                Boolean.TRUE.equals(insurance.getIsPrimary())
        );
    }

    private List<CoverageClassDTO> buildCoverageClassList(PatientInsurance insurance) {
        String policyClassName = clean(insurance.getPolicyClassName());

        if (policyClassName == null) {
            return List.of();
        }

        return List.of(
                new CoverageClassDTO(
                        "plan",
                        policyClassName,
                        policyClassName
                )
        );
    }

    private EligibilityCheckResponse toResult(WaseelEligibilityRequest log) {
        return new EligibilityCheckResponse(
                log.getId(),
                log.getApiStatus(),
                log.getStatusCode(),
                log.getMessage(),
                log.getEligibilityResponseId(),
                log.getEligibilityResponseUrl(),
                log.getRequestStatus()
        );
    }

    private LocalDate resolveServiceDate(LocalDate serviceDate) {
        return serviceDate != null ? serviceDate : LocalDate.now();
    }

    private Integer toInteger(BigDecimal value) {
        return value == null ? null : value.intValue();
    }

    private String buildFullName(Patient patient) {
        String fullName = String.join(
                " ",
                nullToEmpty(patient.getFirstName()),
                nullToEmpty(patient.getSecondName()),
                nullToEmpty(patient.getThirdName()),
                nullToEmpty(patient.getLastName())
        ).trim();

        return fullName.isEmpty() ? null : fullName;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String text = value.trim();

        return text.isEmpty() ? null : text;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
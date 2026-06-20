package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.CoverageClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityBeneficiaryDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityInsurancePlanDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityCheckRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityCheckResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApLovMapperService;
import com.dazzle.asklepios.integration.waseel.service.mapper.AsklepiosLovCodes;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
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
    private final ApLovMapperService apLovMapperService;

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

        System.out.println("WASEEL REQUEST JSON = " + toJson(waseelRequest));

        WaseelEligibilityRequest log = WaseelEligibilityRequest.builder()
                .patientId(patient.getId())
                .patientInsuranceId(insurance.getId())
                .payorId(null)
                .planId(null)
                .providerId(properties.providerId())
                .destinationId(resolveDestinationId(request.destinationId(), insurance))
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
                resolveDocumentType(patient),
                clean(patient.getFirstName()),
                clean(patient.getSecondName()),
                clean(patient.getThirdName()),
                clean(patient.getLastName()),
                buildFullName(patient),
                patient.getDateOfBirth() == null ? null : patient.getDateOfBirth().toString(),
                patient.getSexAtBirth() == null ? null : patient.getSexAtBirth().name(),
                apLovMapperService.getCleanValueCodeByLovCodeAndKey(
                        AsklepiosLovCodes.NATIONALITY,
                        patient.getNationality()
                ),
                clean(patient.getPrimaryMobileNumber()),
                clean(patient.getEmail()),
                clean(patient.getEmergencyContactPhone()),
                null,
                null,
                null,
                null,
                resolveMaritalStatusForWaseel(patient),
                apLovMapperService.getCleanValueCodeByLovCodeAndKey(
                        AsklepiosLovCodes.RELIGION,
                        patient.getReligion()
                ),
                resolveOccupationForWaseel(patient),
                apLovMapperService.getCleanValueCodeByLovCodeAndKey(
                        AsklepiosLovCodes.LANG,
                        patient.getPreferredLanguage()
                ),
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
                resolveDestinationId(request.destinationId(), insurance)
        );
    }

    private EligibilityInsurancePlanDTO buildInsurancePlan(PatientInsurance insurance) {
        return new EligibilityInsurancePlanDTO(
                null,
                clean(insurance.getPayerNphiesId()),
                clean(insurance.getPayerName()),
                clean(insurance.getMemberCardId()),
                clean(insurance.getPolicyNumber()),
                clean(insurance.getPayerNphiesId()),
                clean(insurance.getTpaNphiesId()),
                insurance.getExpirationDate() == null ? null : insurance.getExpirationDate().toString(),
                clean(insurance.getRelationWithSubscriber()),
                clean(insurance.getCoverageType()),
                null,
                null,
                buildCoverageClassList(insurance),
                resolvePolicyHolder(insurance),
                Boolean.TRUE.equals(insurance.getIsPrimary())
        );
    }

    private String resolveDestinationId(String requestDestinationId, PatientInsurance insurance) {
        String destinationId = clean(requestDestinationId);
        if (destinationId != null) {
            return destinationId;
        }

        String tpaNphiesId = clean(insurance.getTpaNphiesId());
        if (tpaNphiesId != null) {
            return tpaNphiesId;
        }

        String payerNphiesId = clean(insurance.getPayerNphiesId());
        if (payerNphiesId != null) {
            return payerNphiesId;
        }

        return "-1";
    }

    private String resolvePolicyHolder(PatientInsurance insurance) {
        String policyHolderName = clean(insurance.getPolicyHolderName());
        if (policyHolderName != null) {
            return policyHolderName;
        }

        String policyNumber = clean(insurance.getPolicyNumber());
        if (policyNumber != null) {
            return policyNumber;
        }

        return clean(insurance.getMemberCardId());
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

    private String resolveDocumentType(Patient patient) {
        String documentId = clean(patient.getDocumentId());

        if (documentId == null) {
            return null;
        }

        if (documentId.startsWith("1")) {
            return "NI";
        }

        if (documentId.startsWith("2")) {
            return "PRC";
        }

        return "PPN";
    }

    private LocalDate resolveServiceDate(LocalDate serviceDate) {
        LocalDate today = LocalDate.now();

        return serviceDate == null || serviceDate.isBefore(today)
                ? today
                : serviceDate;
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

    private String resolveMaritalStatusForWaseel(Patient patient) {
        String value = apLovMapperService.mapMaritalStatusKeyToNphies(
                patient.getMaritalStatus()
        );

        if (value == null || value.isBlank()) {
            return "U";
        }

        return switch (value.trim().toUpperCase()) {
            case "M", "MARRIED" -> "M";
            case "D", "DIVORCED" -> "D";
            case "W", "WIDOWED" -> "W";
            case "U", "SINGLE", "UNMARRIED" -> "U";
            case "L", "LEGALY SEPARATED", "LEGALLY SEPARATED" -> "L";
            default -> "U";
        };
    }

    private String resolveOccupationForWaseel(Patient patient) {
        String value = apLovMapperService.mapOccupationKeyToNphies(
                patient.getOccupation()
        );

        if (value == null || value.isBlank()) {
            return "unknown";
        }

        String normalized = value.trim().toLowerCase();

        return switch (normalized) {
            case "administration",
                 "agriculture",
                 "business",
                 "education",
                 "housewife",
                 "marine",
                 "medical field",
                 "military",
                 "skilled worker",
                 "student",
                 "oil industries",
                 "unemployed",
                 "others",
                 "unknown" -> normalized;
            default -> "unknown";
        };
    }
}
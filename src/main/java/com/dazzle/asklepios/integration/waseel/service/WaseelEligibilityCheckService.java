package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.service.helper.PayorHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.CoverageClassDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityBeneficiaryDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityInsurancePlanDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityCheckRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.request.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityCheckResponse;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.dazzle.asklepios.integration.waseel.event.EligibilityCheckSucceededEvent;
import com.dazzle.asklepios.integration.waseel.service.mapper.ApLovMapperService;
import com.dazzle.asklepios.integration.waseel.service.mapper.AsklepiosLovCodes;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WaseelEligibilityCheckService {

    private static final String ENTITY_NAME = "waseelEligibility";

    private final PatientRepository patientRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final WaseelEligibilityRequestRepository eligibilityLogRepository;
    private final WaseelEligibilityService waseelEligibilityService;
    private final WaseelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final ApLovMapperService apLovMapperService;
    private final EligibilityPatientInsuranceSyncService eligibilityPatientInsuranceSyncService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PayorHelper payorHelper;

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

        validateBeforeWaseelCall(patient, insurance, request);

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

            if (response != null) {
                eligibilityPatientInsuranceSyncService.syncFromEligibilityResponse(
                        insurance,
                        response,
                        saved.getId()
                );
            }

            triggerBackendPreAuthorizationSubmission(request);

            return toResult(saved);

        } catch (HttpClientErrorException ex) {
            markFailed(log, ex.getMessage());
            throw mapWaseelClientError(ex);
        } catch (ResourceAccessException ex) {
            markFailed(log, ex.getMessage());
            throw new BadRequestAlertException(
                    "Unable to connect to Waseel eligibility service",
                    ENTITY_NAME,
                    "waseel.eligibility.connectionFailed"
            );
        } catch (BadRequestAlertException ex) {
            markFailed(log, ex.getBody() == null ? ex.getMessage() : ex.getBody().getDetail());
            throw ex;
        } catch (Exception e) {
            markFailed(log, e.getMessage());
            throw new BadRequestAlertException(
                    "Eligibility check failed",
                    ENTITY_NAME,
                    "waseel.eligibility.failed"
            );
        }
    }

    private void markFailed(WaseelEligibilityRequest log, String message) {
        log.setRequestStatus("FAILED");
        log.setRespondedAt(Instant.now());
        log.setMessage(message);
        eligibilityLogRepository.save(log);
    }

    private BadRequestAlertException mapWaseelClientError(HttpClientErrorException ex) {
        String waseelMessage = extractWaseelErrorMessage(ex.getResponseBodyAsString());

        if (waseelMessage != null && waseelMessage.toLowerCase().contains("failed to read request")) {
            return new BadRequestAlertException(
                    "Waseel rejected the eligibility request because the payload was invalid. "
                            + "Verify patient and insurance details, then try again.",
                    ENTITY_NAME,
                    "waseel.eligibility.invalidRequest"
            );
        }

        if (ex instanceof HttpClientErrorException.BadRequest) {
            return new BadRequestAlertException(
                    waseelMessage != null ? waseelMessage : "Waseel rejected the eligibility request",
                    ENTITY_NAME,
                    "waseel.eligibility.badRequest"
            );
        }

        return new BadRequestAlertException(
                waseelMessage != null ? waseelMessage : "Waseel eligibility request failed",
                ENTITY_NAME,
                "waseel.eligibility.failed"
        );
    }

    private String extractWaseelErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(responseBody);

            if (node.hasNonNull("detail")) {
                return node.get("detail").asText();
            }
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
            if (node.hasNonNull("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
            // ignore parsing error
        }

        return responseBody.trim();
    }

    private void triggerBackendPreAuthorizationSubmission(EligibilityCheckRequest request) {
        if (request == null || request.encounterId() == null) {
            return;
        }

        applicationEventPublisher.publishEvent(
                new EligibilityCheckSucceededEvent(request.encounterId())
        );
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
                resolveGenderForWaseel(patient.getSexAtBirth()),
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
                null,
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
                null,
                resolveDestinationId(request.destinationId(), insurance)
        );
    }

    private void validateBeforeWaseelCall(
            Patient patient,
            PatientInsurance insurance,
            EligibilityCheckRequest request
    ) {
        if (insurance.getExpirationDate() == null
                || insurance.getExpirationDate().isBefore(LocalDate.now())) {
            throw new BadRequestAlertException(
                    "Insurance plan is expired.",
                    ENTITY_NAME,
                    "insurance.expired"
            );
        }

        if (isBlank(insurance.getMemberCardId())) {
            throw new BadRequestAlertException(
                    "Member card ID is required before checking eligibility.",
                    ENTITY_NAME,
                    "memberCardId.required"
            );
        }

        if (isBlank(insurance.getPolicyNumber())) {
            throw new BadRequestAlertException(
                    "Policy number is required before checking eligibility.",
                    ENTITY_NAME,
                    "policyNumber.required"
            );
        }

        if (isBlank(patient.getDocumentId())) {
            throw new BadRequestAlertException(
                    "Patient document ID is required before checking eligibility.",
                    ENTITY_NAME,
                    "patient.documentId.required"
            );
        }

        if (patient.getDateOfBirth() == null) {
            throw new BadRequestAlertException(
                    "Patient date of birth is required before checking eligibility.",
                    ENTITY_NAME,
                    "patient.dateOfBirth.required"
            );
        }

        if (patient.getSexAtBirth() == null) {
            throw new BadRequestAlertException(
                    "Patient gender is required before checking eligibility.",
                    ENTITY_NAME,
                    "patient.gender.required"
            );
        }

        if (isBlank(patient.getFirstName()) || isBlank(patient.getLastName())) {
            throw new BadRequestAlertException(
                    "Patient first name and last name are required before checking eligibility.",
                    ENTITY_NAME,
                    "patient.name.required"
            );
        }
    }

    private EligibilityInsurancePlanDTO buildInsurancePlan(PatientInsurance insurance) {
        String payerNphiesId = resolvePayerNphiesId(insurance);
        String tpaNphiesId = resolveTpaNphiesId(insurance);

        return new EligibilityInsurancePlanDTO(
                null,
                payerNphiesId,
                clean(insurance.getPayerName()),
                clean(insurance.getMemberCardId()),
                clean(insurance.getPolicyNumber()),
                payerNphiesId,
                tpaNphiesId,
                insurance.getExpirationDate() == null ? null : insurance.getExpirationDate().toString(),
                normalizeRelationWithSubscriber(insurance.getRelationWithSubscriber()),
                firstNonBlank(clean(insurance.getCoverageType()), "EHCPOL"),
                null,
                null,
                buildCoverageClassList(insurance),
                resolvePolicyHolder(insurance),
                Boolean.TRUE.equals(insurance.getIsPrimary())
        );
    }

    private String resolvePayerNphiesId(PatientInsurance insurance) {
        String stored = clean(insurance.getPayerNphiesId());
        if (!isBlank(stored)) {
            return stored;
        }

        PayorDTO payor = payorHelper.findPayor(insurance.getPayorId(), null);
        if (payor == null) {
            return null;
        }

        return firstNonBlank(
                clean(payor.nphiesId()),
                clean(payor.waseelPayerId())
        );
    }

    private String resolveTpaNphiesId(PatientInsurance insurance) {
        String stored = clean(insurance.getTpaNphiesId());
        if (!isBlank(stored)) {
            return stored;
        }

        PayorDTO payor = payorHelper.findPayor(insurance.getPayorId(), insurance.getPayerNphiesId());
        if (payor == null) {
            return null;
        }

        return clean(payor.tpaNphiesId());
    }

    private String resolveDestinationId(String requestDestinationId, PatientInsurance insurance) {
        String destinationId = clean(requestDestinationId);
        if (destinationId != null) {
            return destinationId;
        }

        String tpaNphiesId = resolveTpaNphiesId(insurance);
        if (tpaNphiesId != null) {
            return tpaNphiesId;
        }

        return resolvePayerNphiesId(insurance);
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
            case "UNK", "UNKNOWN" -> "UNK";
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
                 "unemployed" -> normalized;
            case "others", "other" -> "others";
            case "unknown" -> "unknown";
            default -> "unknown";
        };
    }

    private String resolveGenderForWaseel(Gender gender) {
        if (gender == null) {
            return null;
        }

        return gender.name().toLowerCase(Locale.ROOT);
    }

    private String normalizeRelationWithSubscriber(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return "self";
        }

        return cleaned.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }

        return null;
    }
}
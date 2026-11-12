package com.dazzle.asklepios.integration.nahdi.service;

import com.dazzle.asklepios.service.SubmittedPrescriptionEditPolicy;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.integration.nahdi.client.NahdiClient;
import com.dazzle.asklepios.integration.nahdi.config.NahdiProperties;
import com.dazzle.asklepios.integration.nahdi.domain.NahdiIntegrationLog;
import com.dazzle.asklepios.integration.nahdi.domain.enumeration.NahdiIntegrationStatus;
import com.dazzle.asklepios.integration.nahdi.dto.NahdiIntegrationLogDTO;
import com.dazzle.asklepios.integration.nahdi.dto.NahdiSendResult;
import com.dazzle.asklepios.integration.nahdi.dto.NahdiSyncStatus;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiSendPrescriptionRequest;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiSendPrescriptionResponse;
import com.dazzle.asklepios.integration.nahdi.mapper.NahdiPrescriptionMapper;
import com.dazzle.asklepios.integration.nahdi.repository.NahdiIntegrationLogRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Optional;

/**
 * Orchestrates outbound integration flows with Nahdi.
 * <p>
 * Contract: methods of this service never propagate failures to the calling clinical flow
 * (e.g. prescription submit). Every attempt is recorded through {@link NahdiIntegrationLogService}.
 */
@Service
@RequiredArgsConstructor
public class NahdiIntegrationService {

    private static final Logger LOG = LoggerFactory.getLogger(NahdiIntegrationService.class);

    private static final String MASKED = "***";

    private final NahdiProperties nahdiProperties;
    private final NahdiClient nahdiClient;
    private final NahdiPrescriptionMapper nahdiPrescriptionMapper;
    private final NahdiIntegrationLogService nahdiIntegrationLogService;
    private final PatientPrescriptionRepository patientPrescriptionRepository;
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    private final NahdiIntegrationLogRepository nahdiIntegrationLogRepository;
    private final ObjectMapper objectMapper;
    private final SubmittedPrescriptionEditPolicy submittedPrescriptionEditPolicy;

    /**
     * Sends a submitted prescription to Nahdi and records the exchange. Never throws.
     *
     * @param prescriptionId the id of the submitted prescription.
     */
    public NahdiSendResult sendPrescription(Long prescriptionId) {
        if (!nahdiProperties.enabled()) {
            LOG.debug("[NAHDI][SEND_PRESCRIPTION] integration disabled, skipping prescriptionId={}", prescriptionId);
            return NahdiSendResult.skipped("Nahdi integration is disabled");
        }
        if (prescriptionId == null) {
            LOG.warn("[NAHDI][SEND_PRESCRIPTION] skipped: prescriptionId is null");
            return NahdiSendResult.skipped("Prescription id is missing");
        }
        if (!StringUtils.hasText(nahdiProperties.baseUrl())) {
            LOG.warn("[NAHDI][SEND_PRESCRIPTION] skipped: nahdi.base-url is not configured. prescriptionId={}", prescriptionId);
            return NahdiSendResult.skipped("Nahdi base URL is not configured");
        }

        LOG.info("[NAHDI][SEND_PRESCRIPTION] start prescriptionId={}", prescriptionId);

        NahdiSendPrescriptionRequest request;
        try {
            request = nahdiPrescriptionMapper.toSendPrescriptionRequest(prescriptionId);
        } catch (Exception e) {
            LOG.error("[NAHDI][SEND_PRESCRIPTION] failed to build request. prescriptionId={}", prescriptionId, e);
            record(prescriptionId, null, "Request build failed: " + e.getMessage(), NahdiIntegrationStatus.FAILED, null);
            return NahdiSendResult.failed("Request build failed: " + e.getMessage());
        }

        String requestPayload = toMaskedJson(request);
        try {
            String response = nahdiClient.sendPrescription(request);
            NahdiSendPrescriptionResponse parsed = parseResponse(response);
            // Nahdi answers HTTP 200 even on business errors; "success" in the body decides.
            if (parsed != null && Boolean.TRUE.equals(parsed.success())) {
                LOG.info("[NAHDI][SEND_PRESCRIPTION] success prescriptionId={}, orderNumber={}", prescriptionId, parsed.orderNumber());
                record(prescriptionId, requestPayload, response, NahdiIntegrationStatus.SUCCESS, parsed.orderNumber());
                return NahdiSendResult.success(parsed.orderNumber());
            }
            LOG.error("[NAHDI][SEND_PRESCRIPTION] rejected by Nahdi. prescriptionId={}, response={}", prescriptionId, response);
            record(prescriptionId, requestPayload, response, NahdiIntegrationStatus.FAILED, null);
            return NahdiSendResult.failed(rejectionMessage(parsed, response));
        } catch (RestClientResponseException e) {
            LOG.error("[NAHDI][SEND_PRESCRIPTION] Nahdi responded with status={} prescriptionId={}",
                    e.getStatusCode().value(), prescriptionId);
            String message = "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString();
            record(prescriptionId, requestPayload, message, NahdiIntegrationStatus.FAILED, null);
            return NahdiSendResult.failed(message);
        } catch (Exception e) {
            LOG.error("[NAHDI][SEND_PRESCRIPTION] call failed. prescriptionId={}, error={}", prescriptionId, e.getMessage());
            record(prescriptionId, requestPayload, "Call failed: " + e.getMessage(), NahdiIntegrationStatus.FAILED, null);
            return NahdiSendResult.failed("Call failed: " + e.getMessage());
        }
    }

    /**
     * Sends the current state of an already submitted prescription again (after edits), with the same reference.
     * Unlike submit, the caller is told the outcome.
     */
    public NahdiSendResult resendPrescription(Long prescriptionId) {
        PatientPrescription prescription = patientPrescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new NotFoundAlertException("PatientPrescription not found: " + prescriptionId, "prescription", "notfound"));
        if (!PrescriptionStatus.SUBMITTED.equals(prescription.getStatus())) {
            throw new BadRequestAlertException("Only a submitted prescription can be resent", "prescription", "notSubmitted");
        }
        submittedPrescriptionEditPolicy.assertCanEdit(prescription);
        LOG.info("[NAHDI][RESEND_PRESCRIPTION] prescriptionId={}", prescriptionId);
        return sendPrescription(prescriptionId);
    }

    /**
     * Tells whether a submitted prescription was changed after its last successful send, so the user knows to resend.
     */
    @Transactional(readOnly = true)
    public NahdiSyncStatus syncStatus(Long prescriptionId) {
        PatientPrescription prescription = patientPrescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new NotFoundAlertException("PatientPrescription not found: " + prescriptionId, "prescription", "notfound"));

        Optional<NahdiIntegrationLog> lastAttempt = nahdiIntegrationLogRepository.findFirstByPrescriptionIdOrderByCreatedDateDesc(prescriptionId);
        Instant lastSentAt = nahdiIntegrationLogRepository
                .findFirstByPrescriptionIdAndStatusOrderByCreatedDateDesc(prescriptionId, NahdiIntegrationStatus.SUCCESS)
                .map(NahdiIntegrationLog::getCreatedDate)
                .orElse(null);
        String lastAttemptStatus = lastAttempt.map(log -> log.getStatus().name()).orElse(null);

        if (!PrescriptionStatus.SUBMITTED.equals(prescription.getStatus())) {
            return new NahdiSyncStatus(false, lastSentAt, lastAttemptStatus);
        }

        Instant baseline = lastSentAt;
        if (baseline == null) {
            if (lastAttempt.isPresent()) {
                // Attempts exist but none was accepted: the prescription still has to reach Nahdi.
                return new NahdiSyncStatus(true, null, lastAttemptStatus);
            }
            // Sent before sends were linked to prescriptions: the submit time is the last send.
            baseline = prescription.getSubmitedDate();
        }
        if (baseline == null) {
            return new NahdiSyncStatus(false, null, lastAttemptStatus);
        }

        Instant since = baseline;
        boolean changed = patientPrescriptionMedicationRepository.findByPrescriptionHeader_Id(prescriptionId).stream()
                .map(medication -> medication.getLastModifiedDate() != null ? medication.getLastModifiedDate() : medication.getCreatedDate())
                .anyMatch(modifiedAt -> modifiedAt != null && modifiedAt.isAfter(since));
        return new NahdiSyncStatus(changed, lastSentAt, lastAttemptStatus);
    }

    private static String rejectionMessage(NahdiSendPrescriptionResponse parsed, String response) {
        if (parsed == null) {
            return "Unexpected response from Nahdi";
        }
        if (StringUtils.hasText(parsed.message())) {
            return parsed.message();
        }
        return parsed.error() != null && !parsed.error().isNull() ? parsed.error().toString() : response;
    }

    /**
     * Returns null when the body is not the expected JSON, which is then treated as a failure.
     */
    private NahdiSendPrescriptionResponse parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(response, NahdiSendPrescriptionResponse.class);
        } catch (Exception e) {
            LOG.warn("[NAHDI][SEND_PRESCRIPTION] unexpected response body: {}", e.getMessage());
            return null;
        }
    }

    private void record(Long prescriptionId, String requestPayload, String responsePayload, NahdiIntegrationStatus status, String orderNumber) {
        try {
            nahdiIntegrationLogService.save(new NahdiIntegrationLogDTO(
                    null, prescriptionId, nahdiProperties.sendPrescriptionEndpoint(), requestPayload, responsePayload, status, orderNumber, null));
        } catch (Exception e) {
            LOG.error("[NAHDI][SEND_PRESCRIPTION] failed to save integration log, status={}", status, e);
        }
    }

    private String toMaskedJson(NahdiSendPrescriptionRequest request) {
        try {
            NahdiSendPrescriptionRequest masked = request.presHead() == null
                    ? request
                    : new NahdiSendPrescriptionRequest(
                            request.presHead().toBuilder().providerToken(MASKED).build(),
                            request.presDiagnosis(),
                            request.presItem());
            return objectMapper.writeValueAsString(masked);
        } catch (Exception e) {
            LOG.warn("[NAHDI][SEND_PRESCRIPTION] failed to serialize request for logging: {}", e.getMessage());
            return null;
        }
    }
}

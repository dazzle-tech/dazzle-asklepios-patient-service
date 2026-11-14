package com.dazzle.asklepios.integration.nahdi.service;

import com.dazzle.asklepios.integration.nahdi.client.NahdiClient;
import com.dazzle.asklepios.integration.nahdi.config.NahdiProperties;
import com.dazzle.asklepios.integration.nahdi.domain.enumeration.NahdiIntegrationStatus;
import com.dazzle.asklepios.integration.nahdi.dto.NahdiIntegrationLogDTO;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiSendPrescriptionRequest;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiSendPrescriptionResponse;
import com.dazzle.asklepios.integration.nahdi.mapper.NahdiPrescriptionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

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
    private final ObjectMapper objectMapper;

    /**
     * Sends a submitted prescription to Nahdi and records the exchange.
     *
     * @param prescriptionId the id of the submitted prescription.
     */
    public void sendPrescription(Long prescriptionId) {
        if (!nahdiProperties.enabled()) {
            LOG.debug("[NAHDI][SEND_PRESCRIPTION] integration disabled, skipping prescriptionId={}", prescriptionId);
            return;
        }
        if (prescriptionId == null) {
            LOG.warn("[NAHDI][SEND_PRESCRIPTION] skipped: prescriptionId is null");
            return;
        }
        if (!StringUtils.hasText(nahdiProperties.baseUrl())) {
            LOG.warn("[NAHDI][SEND_PRESCRIPTION] skipped: nahdi.base-url is not configured. prescriptionId={}", prescriptionId);
            return;
        }

        LOG.info("[NAHDI][SEND_PRESCRIPTION] start prescriptionId={}", prescriptionId);

        NahdiSendPrescriptionRequest request;
        try {
            request = nahdiPrescriptionMapper.toSendPrescriptionRequest(prescriptionId);
        } catch (Exception e) {
            LOG.error("[NAHDI][SEND_PRESCRIPTION] failed to build request. prescriptionId={}", prescriptionId, e);
            record(null, "Request build failed: " + e.getMessage(), NahdiIntegrationStatus.FAILED, null);
            return;
        }

        String requestPayload = toMaskedJson(request);
        try {
            String response = nahdiClient.sendPrescription(request);
            NahdiSendPrescriptionResponse parsed = parseResponse(response);
            // Nahdi answers HTTP 200 even on business errors; "success" in the body decides.
            if (parsed != null && Boolean.TRUE.equals(parsed.success())) {
                LOG.info("[NAHDI][SEND_PRESCRIPTION] success prescriptionId={}, orderNumber={}", prescriptionId, parsed.orderNumber());
                record(requestPayload, response, NahdiIntegrationStatus.SUCCESS, parsed.orderNumber());
            } else {
                LOG.error("[NAHDI][SEND_PRESCRIPTION] rejected by Nahdi. prescriptionId={}, response={}", prescriptionId, response);
                record(requestPayload, response, NahdiIntegrationStatus.FAILED, null);
            }
        } catch (RestClientResponseException e) {
            LOG.error("[NAHDI][SEND_PRESCRIPTION] Nahdi responded with status={} prescriptionId={}",
                    e.getStatusCode().value(), prescriptionId);
            record(requestPayload, "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), NahdiIntegrationStatus.FAILED, null);
        } catch (Exception e) {
            LOG.error("[NAHDI][SEND_PRESCRIPTION] call failed. prescriptionId={}, error={}", prescriptionId, e.getMessage());
            record(requestPayload, "Call failed: " + e.getMessage(), NahdiIntegrationStatus.FAILED, null);
        }
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

    private void record(String requestPayload, String responsePayload, NahdiIntegrationStatus status, String orderNumber) {
        try {
            nahdiIntegrationLogService.save(new NahdiIntegrationLogDTO(
                    null, nahdiProperties.sendPrescriptionEndpoint(), requestPayload, responsePayload, status, orderNumber, null));
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

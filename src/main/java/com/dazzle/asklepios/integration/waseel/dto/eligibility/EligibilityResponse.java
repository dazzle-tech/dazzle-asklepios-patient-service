package com.dazzle.asklepios.integration.waseel.dto.eligibility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibilityResponse(
        String transactionId,
        String requestId,
        String responseId,
        String eligibilityRequestId,
        String eligibilityResponseId,
        String nphiesResponseId,
        String status,
        String outcome,
        String disposition,
        String siteEligibility,
        String message,
        String apiStatus,
        String statusCode,
        Map<String, Object> data,
        Object errors
) {}
package com.dazzle.asklepios.integration.waseel.dto.eligibility.response;

public record EligibilityCheckResponse(
        Long eligibilityRequestId,
        String apiStatus,
        String statusCode,
        String message,
        String eligibilityResponseId,
        String eligibilityResponseUrl,
        String requestStatus
) {
}
package com.dazzle.asklepios.integration.waseel.dto.eligibility;

public record EligibilityCheckResult(
        Long eligibilityRequestId,
        String apiStatus,
        String statusCode,
        String message,
        String eligibilityResponseId,
        String eligibilityResponseUrl,
        String requestStatus
) {
}
package com.dazzle.asklepios.integration.waseel.dto.eligibility.response;

public record EligibilityCheckResponse(
        Long eligibilityRequestId,
        String apiStatus,
        String statusCode,
        String message,
        String eligibilityResponseId,
        String eligibilityResponseUrl,
        String requestStatus,
        /** False when the payer answered that the patient is not eligible; null when this check did not tell. */
        Boolean eligible,
        String notEligibleReason
) {
}
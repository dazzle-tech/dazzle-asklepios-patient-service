package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payer decision entered by the user for a local (NPHIES contract) pre-approval request.
 */
public record LocalPreAuthorizationDecisionRequest(
        @NotNull Decision decision,
        @Size(max = 1000) String note
) {
    public enum Decision {
        APPROVED,
        PARTIAL,
        REJECTED
    }
}

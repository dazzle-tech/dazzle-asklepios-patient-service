package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payer decision entered by the user for a local (NPHIES contract) pre-approval request.
 * {@code approvedAmount} is the insurance amount approved on a PARTIAL decision.
 */
public record LocalPreAuthorizationDecisionRequest(
        @NotNull Decision decision,
        @Size(max = 1000) String note,
        @DecimalMin(value = "0.01") BigDecimal approvedAmount
) {
    public enum Decision {
        APPROVED,
        PARTIAL,
        REJECTED
    }
}

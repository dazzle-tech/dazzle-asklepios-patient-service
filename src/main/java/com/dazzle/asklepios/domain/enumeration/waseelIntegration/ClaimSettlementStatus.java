package com.dazzle.asklepios.domain.enumeration.waseelIntegration;

/**
 * Summary of one statement of account. It never replaces the status of an individual claim.
 */
public enum ClaimSettlementStatus {
    READY_FOR_SUBMISSION,
    PARTIALLY_SUBMITTED,
    INVOICES_SUCCESSFULLY_SUBMITTED,
    REQUIRES_ACTION,
    CANCELLED
}

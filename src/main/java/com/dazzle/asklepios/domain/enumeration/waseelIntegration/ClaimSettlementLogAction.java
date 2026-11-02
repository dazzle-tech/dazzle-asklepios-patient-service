package com.dazzle.asklepios.domain.enumeration.waseelIntegration;

/**
 * What happened to an invoice inside a statement of account.
 */
public enum ClaimSettlementLogAction {
    /** The invoice was grouped under the statement. */
    ADDED,
    /** A claim was opened for the invoice while it was under the statement. */
    CLAIM_LINKED,
    /** The claim was rejected, failed or returned, so the invoice left the statement. */
    RELEASED,
    /** A later accepted result brought the invoice back to its statement. */
    RESTORED
}

package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;

/**
 * Pure rules for the statement-of-account lifecycle.
 * Rejected and failed claims leave the statement. Every other result stays in it.
 */
public final class ClaimSettlementRules {

    private ClaimSettlementRules() {
    }

    public static boolean leavesSettlement(ClaimStatus status) {
        return status == ClaimStatus.REJECTED || status == ClaimStatus.FAILED;
    }

    /**
     * @param activeCount invoices still under the statement
     * @param releasedCount invoices that already left it
     * @param linkedActiveCount active invoices that already have a claim which did not fail
     */
    public static ClaimSettlementStatus summarize(
            int activeCount,
            int releasedCount,
            int linkedActiveCount
    ) {
        if (activeCount == 0 && releasedCount > 0) {
            return ClaimSettlementStatus.REQUIRES_ACTION;
        }
        if (releasedCount > 0 && activeCount > 0) {
            return ClaimSettlementStatus.PARTIALLY_SUBMITTED;
        }
        if (activeCount > 0 && linkedActiveCount == activeCount) {
            return ClaimSettlementStatus.INVOICES_SUCCESSFULLY_SUBMITTED;
        }
        return ClaimSettlementStatus.READY_FOR_SUBMISSION;
    }
}

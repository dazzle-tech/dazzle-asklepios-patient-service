package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimSettlementRulesTest {

    @Test
    void rejectedAndFailedClaimsLeaveTheStatement() {
        assertTrue(ClaimSettlementRules.leavesSettlement(ClaimStatus.REJECTED));
        assertTrue(ClaimSettlementRules.leavesSettlement(ClaimStatus.FAILED));
        assertFalse(ClaimSettlementRules.leavesSettlement(ClaimStatus.ACCEPTED));
        assertFalse(ClaimSettlementRules.leavesSettlement(ClaimStatus.SUBMITTED));
        assertFalse(ClaimSettlementRules.leavesSettlement(ClaimStatus.SUBMITTING));
        assertFalse(ClaimSettlementRules.leavesSettlement(ClaimStatus.DRAFT));
    }

    @Test
    void statementStatusSummarizesItsInvoices() {
        assertEquals(
                ClaimSettlementStatus.READY_FOR_SUBMISSION,
                ClaimSettlementRules.summarize(3, 0, 0)
        );
        assertEquals(
                ClaimSettlementStatus.READY_FOR_SUBMISSION,
                ClaimSettlementRules.summarize(3, 0, 1)
        );
        assertEquals(
                ClaimSettlementStatus.INVOICES_SUCCESSFULLY_SUBMITTED,
                ClaimSettlementRules.summarize(3, 0, 3)
        );
        assertEquals(
                ClaimSettlementStatus.PARTIALLY_SUBMITTED,
                ClaimSettlementRules.summarize(2, 1, 2)
        );
        assertEquals(
                ClaimSettlementStatus.REQUIRES_ACTION,
                ClaimSettlementRules.summarize(0, 2, 0)
        );
    }
}

package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;

import java.math.BigDecimal;

public record BillingAllocationReversalResult(

        Long allocationId,

        String allocationNumber,

        BigDecimal reversedAmount,

        BigDecimal remainingAllocatedAmount,

        BigDecimal responsibilityOutstandingAmount,

        BigDecimal chargeLineOutstandingAmount,

        BigDecimal chargeOutstandingAmount,

        BigDecimal walletAvailableBalance,

        BigDecimal walletConsumedAmount,

        BillingAllocationStatus allocationStatus

) {
}
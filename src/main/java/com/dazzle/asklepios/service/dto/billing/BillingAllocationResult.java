package com.dazzle.asklepios.service.dto.billing;

import java.math.BigDecimal;

public record BillingAllocationResult(

        Long allocationId,
        String allocationNumber,

        Long reservationId,
        Long chargeId,
        Long chargeLineId,
        Long responsibilityId,
        Long patientServiceProductId,

        BigDecimal allocatedAmount,

        BigDecimal responsibilityOutstandingAmount,
        BigDecimal chargeLineOutstandingAmount,
        BigDecimal chargeOutstandingAmount,

        BigDecimal walletAvailableBalance,
        BigDecimal walletReservedBalance,
        BigDecimal walletConsumedAmount

) {
}
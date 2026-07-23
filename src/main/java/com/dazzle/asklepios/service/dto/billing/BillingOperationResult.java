package com.dazzle.asklepios.service.dto.billing;

import java.math.BigDecimal;

public record BillingOperationResult(

        Long patientServiceProductId,

        Long chargeId,

        Long chargeLineId,

        Long pricingSnapshotId,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal exemptionAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        BigDecimal patientResponsibilityAmount,

        BigDecimal insuranceResponsibilityAmount,

        BigDecimal reservedAmount,

        boolean processed,

        String message

) {

    public static BillingOperationResult skipped(
            Long patientServiceProductId,
            String message
    ) {
        return new BillingOperationResult(
                patientServiceProductId,
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                message
        );
    }
}
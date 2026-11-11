package com.dazzle.asklepios.web.rest.vm.billing;

import com.dazzle.asklepios.domain.DiscountCodeUsage;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record DiscountCodeUsageVM(
        Long id,
        Long encounterId,
        Long patientServiceProductId,
        Long chargeLineId,
        String itemDescription,
        Long serviceId,
        Long procedureId,
        Long authorizedUserId,
        String code,
        String discountName,
        DiscountType discountType,
        BigDecimal percentage,
        BigDecimal fixedAmount,
        Currency currency,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        Instant appliedAt
) {

    public static DiscountCodeUsageVM of(DiscountCodeUsage usage, String itemDescription) {
        return new DiscountCodeUsageVM(
                usage.getId(),
                usage.getEncounterId(),
                usage.getPatientServiceProductId(),
                usage.getChargeLineId(),
                itemDescription,
                usage.getServiceId(),
                usage.getProcedureId(),
                usage.getAuthorizedUserId(),
                usage.getCode(),
                usage.getDiscountName(),
                usage.getDiscountType(),
                usage.getPercentage(),
                usage.getFixedAmount(),
                usage.getCurrency(),
                usage.getGrossAmount(),
                usage.getDiscountAmount(),
                usage.getCreatedDate()
        );
    }
}

package com.dazzle.asklepios.client.setup.dto;

import java.math.BigDecimal;

public record SetupPricingResponse(
        Long priceListId,
        Long priceListItemId,
        Long billingConfigurationId,

        Long discountId,
        String discountType,
        BigDecimal discountRate,
        BigDecimal discountAmount,

        Long taxId,
        String taxType,
        BigDecimal taxRate,

        BigDecimal unitPrice,
        String currency,

        String calculationOrder,
        String roundingMode,
        Integer roundingScale,

        String itemCode,
        String itemName
) {
}
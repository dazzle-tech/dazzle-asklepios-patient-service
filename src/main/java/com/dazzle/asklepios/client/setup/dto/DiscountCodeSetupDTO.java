package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscountCodeSetupDTO(
        Long id,
        Long facilityId,
        String code,
        String name,
        DiscountType discountType,
        BigDecimal percentage,
        BigDecimal fixedAmount,
        Currency currency,
        Boolean isActive
) {
}

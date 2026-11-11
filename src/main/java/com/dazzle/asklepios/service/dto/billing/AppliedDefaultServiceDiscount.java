package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;

import java.math.BigDecimal;

/**
 * Discount authorized for the default services of the current preview or prepare call.
 */
public record AppliedDefaultServiceDiscount(
        Long discountCodeId,
        String code,
        String name,
        DiscountType discountType,
        BigDecimal percentage,
        BigDecimal fixedAmount,
        Currency currency
) {
}

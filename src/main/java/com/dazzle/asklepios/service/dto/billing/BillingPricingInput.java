package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.TaxCalculationType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.CalculationOrder;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.PricingSource;
import com.dazzle.asklepios.domain.enumeration.billing.RoundingModeType;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;

import java.math.BigDecimal;

public record BillingPricingInput(

        Long priceListId,
        Long priceListItemId,
        String priceListCode,
        String priceListName,
        String priceListItemCode,
        Long pricingVersion,

        BillingPriceSource priceSource,
        Long setupSourceId,

        BigDecimal quantity,
        BigDecimal unitPrice,

        BigDecimal setupUnitPrice,

        Long discountId,
        DiscountType discountType,
        BigDecimal discountRate,
        BigDecimal configuredDiscountAmount,

        Long taxId,
        TaxType taxType,
        TaxCalculationType taxCalculationType,
        BigDecimal taxRate,
        BigDecimal taxFixedAmount,

        Currency currency,
        PricingSource pricingSource,

        CalculationOrder calculationOrder,
        RoundingModeType roundingMode,
        Integer roundingScale,

        String itemCode,
        String itemName

) {
}
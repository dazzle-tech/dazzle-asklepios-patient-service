package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.TaxCalculationType;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.PricingSource;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingPricingResolveResponse(

        Long priceListId,
        Long priceListItemId,
        String priceListCode,
        String priceListName,
        String priceListItemCode,
        Long pricingVersion,

        String itemCode,
        String itemName,

        BigDecimal unitPrice,
        Currency currency,
        PricingSource pricingSource,

        Long taxId,
        TaxType taxType,
        TaxCalculationType taxCalculationType,
        BigDecimal taxRate,
        BigDecimal taxFixedAmount,

        Long discountId,
        DiscountType discountType,
        BigDecimal discountRate,
        BigDecimal discountFixedAmount,

        String calculationOrder,
        String roundingMode,
        Integer roundingScale

) implements Serializable {
}}
package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.enumeration.TaxCalculationType;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;

import java.math.BigDecimal;

public record BillingAdjustmentResolveResponse(

        Long taxId,

        String taxCode,

        String taxName,

        TaxApplicableOn taxApplicableOn,

        TaxType taxType,

        TaxCalculationType taxCalculationType,

        BigDecimal taxRate,

        BigDecimal taxFixedAmount,

        Long discountId,

        String discountCode,

        String discountName,

        DiscountApplicableOn discountApplicableOn,

        DiscountType discountType,

        BigDecimal discountRate,

        BigDecimal discountFixedAmount

) {
}

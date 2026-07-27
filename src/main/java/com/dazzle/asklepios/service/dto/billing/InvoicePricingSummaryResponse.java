package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.TaxCalculationType;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record InvoicePricingSummaryResponse(

        Long invoiceId,

        String documentNumber,

        Currency currency,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        List<AppliedDiscountRule> discountRules,

        List<AppliedTaxRule> taxRules

) implements Serializable {

    public record AppliedDiscountRule(

            Long ruleId,

            String code,

            String name,

            DiscountApplicableOn applicableOn,

            DiscountType discountType,

            BigDecimal rate,

            BigDecimal fixedAmount,

            BigDecimal appliedAmount

    ) implements Serializable {
    }

    public record AppliedTaxRule(

            Long ruleId,

            String code,

            String name,

            TaxApplicableOn applicableOn,

            TaxType taxType,

            TaxCalculationType calculationType,

            BigDecimal rate,

            BigDecimal fixedAmount,

            BigDecimal appliedAmount

    ) implements Serializable {
    }
}

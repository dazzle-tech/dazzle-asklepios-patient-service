package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.TaxCalculationType;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record InvoiceItemPricingAdjustmentSnapshot(

        List<AppliedDiscountEntry> discounts,

        List<AppliedTaxEntry> taxes

) implements Serializable {

    public static InvoiceItemPricingAdjustmentSnapshot empty() {
        return new InvoiceItemPricingAdjustmentSnapshot(
                List.of(),
                List.of()
        );
    }

    public InvoiceItemPricingAdjustmentSnapshot withDiscount(
            AppliedDiscountEntry entry
    ) {
        List<AppliedDiscountEntry> nextDiscounts =
                new ArrayList<>(discounts == null ? List.of() : discounts);
        nextDiscounts.add(entry);
        return new InvoiceItemPricingAdjustmentSnapshot(
                List.copyOf(nextDiscounts),
                taxes == null ? List.of() : taxes
        );
    }

    public InvoiceItemPricingAdjustmentSnapshot withTax(
            AppliedTaxEntry entry
    ) {
        List<AppliedTaxEntry> nextTaxes =
                new ArrayList<>(taxes == null ? List.of() : taxes);
        nextTaxes.add(entry);
        return new InvoiceItemPricingAdjustmentSnapshot(
                discounts == null ? List.of() : discounts,
                List.copyOf(nextTaxes)
        );
    }

    public record AppliedDiscountEntry(

            String source,

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

    public record AppliedTaxEntry(

            String source,

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

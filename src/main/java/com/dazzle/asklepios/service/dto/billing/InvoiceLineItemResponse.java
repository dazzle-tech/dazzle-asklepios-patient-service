package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentItemAdjustmentAction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record InvoiceLineItemResponse(

        Long id,

        Long patientServiceProductId,

        Long chargeLineId,

        String itemCode,

        String itemDescription,

        Long quantity,

        BigDecimal unitPrice,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        BigDecimal paidAmount,

        BigDecimal remainingAmount,

        String status,

        Currency currency,

        List<InvoiceLineAppliedDiscount> appliedDiscounts,

        List<InvoiceLineAppliedTax> appliedTaxes,

        /** INVOICE or DEBIT_NOTE — debit-note lines can be credited when invoice lines are fully paid. */
        String lineSource,

        BigDecimal chargeNetAmount,

        BigDecimal chargeUnitPrice,

        BigDecimal chargeQuantity,

        BigDecimal patientShareAmount,

        BigDecimal insuranceShareAmount,

        BigDecimal patientCopaymentPercentage,

        BigDecimal patientMaximumCopayment

) implements Serializable {

    public record InvoiceLineAppliedDiscount(

            String source,

            Long ruleId,

            String code,

            String name,

            String applicableOn,

            String discountType,

            BigDecimal rate,

            BigDecimal fixedAmount,

            BigDecimal appliedAmount

    ) implements Serializable {
    }

    public record InvoiceLineAppliedTax(

            String source,

            Long ruleId,

            String code,

            String name,

            String applicableOn,

            String taxType,

            String calculationType,

            BigDecimal rate,

            BigDecimal fixedAmount,

            BigDecimal appliedAmount

    ) implements Serializable {
    }
}

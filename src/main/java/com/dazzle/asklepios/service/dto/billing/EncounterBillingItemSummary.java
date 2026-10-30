package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record EncounterBillingItemSummary(

        Long patientServiceProductId,

        Long chargeLineId,

        String billingItemType,

        Long sourceId,

        String itemCode,

        String itemName,

        BigDecimal quantity,

        BigDecimal unitPrice,

        BigDecimal setupUnitPrice,

        String priceSource,

        String priceListItemCode,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal exemptionAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        BigDecimal patientResponsibilityAmount,

        BigDecimal insuranceResponsibilityAmount,

        BigDecimal otherPayerResponsibilityAmount,

        BigDecimal reservedAmount,

        BigDecimal allocatedAmount,

        BigDecimal outstandingAmount,

        boolean exempted,

        Currency currency,

        BillingChargeLineStatus status,

        java.time.Instant chargedAt,

        List<BillingResponsibilitySummary> responsibilities,

        BigDecimal taxRate,

        /*
         * Tax of the patient / insurance share when the tax setup flags
         * split the tax by share; null = original proportional split.
         */
        BigDecimal patientTaxAmount,

        BigDecimal insuranceTaxAmount

) implements Serializable {

    public EncounterBillingItemSummary(
            Long patientServiceProductId,
            Long chargeLineId,
            String billingItemType,
            Long sourceId,
            String itemCode,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal setupUnitPrice,
            String priceSource,
            String priceListItemCode,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal exemptionAmount,
            BigDecimal taxAmount,
            BigDecimal netAmount,
            BigDecimal patientResponsibilityAmount,
            BigDecimal insuranceResponsibilityAmount,
            BigDecimal otherPayerResponsibilityAmount,
            BigDecimal reservedAmount,
            BigDecimal allocatedAmount,
            BigDecimal outstandingAmount,
            boolean exempted,
            Currency currency,
            BillingChargeLineStatus status,
            java.time.Instant chargedAt,
            List<BillingResponsibilitySummary> responsibilities,
            BigDecimal taxRate
    ) {
        this(
                patientServiceProductId,
                chargeLineId,
                billingItemType,
                sourceId,
                itemCode,
                itemName,
                quantity,
                unitPrice,
                setupUnitPrice,
                priceSource,
                priceListItemCode,
                grossAmount,
                discountAmount,
                exemptionAmount,
                taxAmount,
                netAmount,
                patientResponsibilityAmount,
                insuranceResponsibilityAmount,
                otherPayerResponsibilityAmount,
                reservedAmount,
                allocatedAmount,
                outstandingAmount,
                exempted,
                currency,
                status,
                chargedAt,
                responsibilities,
                taxRate,
                null,
                null
        );
    }
}

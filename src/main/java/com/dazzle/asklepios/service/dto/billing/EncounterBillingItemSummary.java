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

        String clinicalStatus,

        BigDecimal taxRate,

        String discountType,

        BigDecimal discountRate,

        String discountSource,

        String discountReason,

        /*
         * Tax carried by the patient share / the insurance share of the line
         * (Patient Copayment / Claim Share). Null when unknown: unbilled
         * items and lines billed before the toggles existed.
         */
        BigDecimal patientTaxAmount,

        BigDecimal insuranceTaxAmount

) implements Serializable {

    /**
     * Summary without the party tax split.
     */
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
            BigDecimal taxRate,
            String discountType,
            BigDecimal discountRate,
            String discountSource,
            String discountReason
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
                null,
                taxRate,
                discountType,
                discountRate,
                discountSource,
                discountReason,
                null,
                null
        );
    }

    public EncounterBillingItemSummary withClinicalStatus(String nextClinicalStatus) {
        if ((clinicalStatus == null && nextClinicalStatus == null)
                || (clinicalStatus != null && clinicalStatus.equals(nextClinicalStatus))) {
            return this;
        }

        return new EncounterBillingItemSummary(
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
                nextClinicalStatus,
                taxRate,
                discountType,
                discountRate,
                discountSource,
                discountReason,
                patientTaxAmount,
                insuranceTaxAmount
        );
    }
}

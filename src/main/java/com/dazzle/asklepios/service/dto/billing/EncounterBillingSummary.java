package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EncounterBillingSummary(

        Long chargeId,

        String chargeNumber,

        Long patientId,

        Long encounterId,

        Instant chargeDate,

        Currency currency,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal exemptionAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        BigDecimal allocatedAmount,

        BigDecimal outstandingAmount,

        Integer lineCount,

        BillingChargeStatus chargeStatus,

        BigDecimal patientResponsibilityAmount,

        BigDecimal patientAllocatedAmount,

        BigDecimal patientOutstandingAmount,

        BigDecimal patientWalletSettledAmount,

        BigDecimal patientDebitSettledAmount,

        BigDecimal insuranceResponsibilityAmount,

        BigDecimal insuranceAllocatedAmount,

        BigDecimal insuranceOutstandingAmount,

        BigDecimal otherPayerResponsibilityAmount,

        BigDecimal otherPayerAllocatedAmount,

        BigDecimal otherPayerOutstandingAmount,

        BillingWalletSummary wallet,

        List<EncounterBillingItemSummary> items

) implements Serializable {
}

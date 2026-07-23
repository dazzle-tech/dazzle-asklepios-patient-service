package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record BillingCheckoutResult(

        Long chargeId,

        String chargeNumber,

        Long patientId,

        Long encounterId,

        BigDecimal netAmount,

        BigDecimal totalReservedAllocated,

        BigDecimal totalAvailableWalletAllocated,

        BigDecimal totalDebitCreated,

        BigDecimal patientOutstandingAmount,

        BigDecimal insuranceOutstandingAmount,

        BigDecimal otherPayerOutstandingAmount,

        BigDecimal totalOutstandingAmount,

        Currency currency,

        BillingChargeStatus chargeStatus,

        boolean patientSettled,

        boolean financiallyClosed,

        List<BillingCheckoutLineResult> lines

) implements Serializable {
}
package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionStatus;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingDebitReversalResult(

        Long originalDebitTransactionId,

        Long reversalTransactionId,

        String reversalTransactionNumber,

        Long allocationId,

        BigDecimal reversedAmount,

        BigDecimal debitBalanceBefore,

        BigDecimal debitBalanceAfter,

        BigDecimal availableCreditAfter,

        BigDecimal responsibilityOutstandingAfter,

        BigDecimal chargeLineOutstandingAfter,

        BigDecimal chargeOutstandingAfter,

        Currency currency,

        BillingDebitTransactionStatus transactionStatus,

        BillingAllocationStatus allocationStatus

) implements Serializable {
}
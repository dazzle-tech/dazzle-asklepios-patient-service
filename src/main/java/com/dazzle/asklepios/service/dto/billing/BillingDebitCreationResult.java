package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionStatus;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingDebitCreationResult(

        Long debitAccountId,
        String debitAccountNumber,

        Long debitTransactionId,
        String debitTransactionNumber,

        Long responsibilityId,
        Long chargeId,
        Long chargeLineId,
        Long patientServiceProductId,

        BigDecimal debitAmount,

        BigDecimal debitBalanceBefore,
        BigDecimal debitBalanceAfter,

        BigDecimal availableCreditAfter,

        Currency currency,

        BillingDebitTransactionStatus transactionStatus

) implements Serializable {
}
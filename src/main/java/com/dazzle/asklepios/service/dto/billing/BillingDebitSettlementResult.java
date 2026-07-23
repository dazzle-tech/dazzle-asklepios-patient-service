package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionStatus;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingDebitSettlementResult(

        Long debitAccountId,

        Long settlementTransactionId,

        String settlementTransactionNumber,

        BigDecimal settlementAmount,

        BigDecimal balanceBefore,

        BigDecimal balanceAfter,

        BigDecimal availableCreditAfter,

        BillingDebitTransactionStatus transactionStatus

) implements Serializable {
}
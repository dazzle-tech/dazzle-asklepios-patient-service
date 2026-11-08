package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundStatus;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingRefundResult(

        Long refundId,

        String refundNumber,

        Long refundPaymentTransactionId,

        String refundPaymentTransactionNumber,

        Long walletId,

        Long originalPaymentId,

        BigDecimal requestedAmount,

        BigDecimal approvedAmount,

        BigDecimal refundedAmount,

        BigDecimal reversedAmount,

        BigDecimal walletAvailableBalance,

        BigDecimal walletReservedBalance,

        BigDecimal walletConsumedAmount,

        BigDecimal walletRefundedAmount,

        Currency currency,

        BillingRefundSourceType refundSourceType,

        BillingRefundStatus status,

        Long financialDocumentId,

        String documentNumber

) implements Serializable {
}

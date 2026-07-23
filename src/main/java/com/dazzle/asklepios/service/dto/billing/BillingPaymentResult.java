package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record BillingPaymentResult(

        Long paymentId,

        String paymentNumber,

        Long paymentTransactionId,

        String transactionNumber,

        Long walletId,

        BigDecimal paymentAmount,

        BigDecimal walletAvailableBalance,

        BigDecimal walletReservedBalance,

        BigDecimal walletConsumedAmount,

        BigDecimal walletRefundedAmount,

        BigDecimal totalReservedForServices,

        Currency currency,

        BillingPaymentStatus paymentStatus,

        BillingPaymentTransactionStatus transactionStatus,

        List<BillingPaymentReservationResult> reservations

) implements Serializable {
}
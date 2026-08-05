package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;

import java.io.Serializable;
import java.math.BigDecimal;

public record CollectInvoiceBalanceResult(

        Long invoiceId,

        String documentNumber,

        Currency currency,

        BigDecimal collectedAmount,

        BigDecimal paidAmount,

        BigDecimal outstandingAmount,

        FinancialDocumentStatus status,

        Long paymentId,

        String paymentNumber,

        String paymentTransactionNumber,

        BigDecimal walletAvailableBalance

) implements Serializable {
}

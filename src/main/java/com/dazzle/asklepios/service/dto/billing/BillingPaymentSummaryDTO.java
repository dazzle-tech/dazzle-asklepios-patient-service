package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.PayerType;
import com.dazzle.asklepios.domain.enumeration.billing.PaymentCategory;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record BillingPaymentSummaryDTO(

        Long paymentId,

        String paymentNumber,

        Long encounterId,

        PaymentCategory paymentCategory,

        PayerType payerType,

        BigDecimal amount,

        Currency currency,

        BillingPaymentStatus status,

        Instant paymentDate,

        String receiptNumber,

        String externalReference,

        String notes

) implements Serializable {
}

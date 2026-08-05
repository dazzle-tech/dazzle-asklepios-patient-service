package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.PayerType;
import com.dazzle.asklepios.domain.enumeration.billing.PaymentCategory;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record CreateAdvancePaymentRequest(

        @NotNull
        Long patientId,

        Long encounterId,

        @NotNull
        PaymentCategory paymentCategory,

        @NotNull
        PayerType payerType,

        Long payerId,

        @NotNull
        @DecimalMin("0.0001")
        BigDecimal amount,

        @NotNull
        Currency currency,

        @NotNull
        Long paymentMethodId,

        @NotBlank
        @Size(max = 50)
        String paymentMethodCode,

        @NotNull
        BillingPaymentStatus paymentStatus,

        @NotNull
        BillingPaymentTransactionType transactionType,

        @NotNull
        BillingPaymentTransactionStatus transactionStatus,

        @Size(max = 100)
        String receiptNumber,

        @Size(max = 150)
        String externalReference,

        @Size(max = 100)
        String authorizationCode,

        @Size(max = 150)
        String processorReference,

        @Pattern(regexp = "^[0-9]{4}$")
        String cardLastFour,

        @Size(max = 150)
        String bankReference,

        Long cashRegisterId,

        String notes,

        /*
         * Empty list means patient-level advance:
         * the full amount stays available.
         *
         * A populated list means service-based advance:
         * reservations are created for these PSP records.
         */
        List<Long> patientServiceProductIds,

        @NotBlank
        @Size(max = 100)
        String requestId

) implements Serializable {
}
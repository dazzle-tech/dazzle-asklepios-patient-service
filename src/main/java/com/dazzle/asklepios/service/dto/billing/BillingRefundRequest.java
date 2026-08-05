package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundSourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingRefundRequest(

        @NotNull
        Long patientId,

        Long encounterId,

        /*
         * Required only for ORIGINAL_PAYMENT refunds.
         */
        Long originalPaymentId,

        Long originalPaymentTransactionId,

        @NotNull
        BillingRefundSourceType refundSourceType,

        @NotNull
        @DecimalMin("0.0001")
        BigDecimal requestedAmount,

        @NotNull
        Long refundMethodId,

        @NotBlank
        @Size(max = 50)
        String refundMethodCode,

        @NotBlank
        @Size(max = 50)
        String requestedBy,

        @NotBlank
        @Size(max = 500)
        String reason,

        @Size(max = 150)
        String externalReference,

        @Size(max = 150)
        String processorReference,

        @Size(max = 50)
        String referenceDocumentType,

        Long referenceDocumentId,

        @Size(max = 150)
        String referenceDocumentNumber,

        String notes,

        @NotBlank
        @Size(max = 100)
        String requestId,

        @NotNull
        BillingLedgerSourceChannel sourceChannel

) implements Serializable {
}
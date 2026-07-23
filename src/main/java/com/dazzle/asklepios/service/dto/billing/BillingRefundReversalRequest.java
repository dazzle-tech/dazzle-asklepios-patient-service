package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingRefundReversalRequest(

        @NotNull
        Long refundId,

        @NotNull
        @DecimalMin(value = "0.0001")
        BigDecimal amount,

        @NotBlank
        @Size(max = 500)
        String reason,

        @NotBlank
        @Size(max = 50)
        String reversedBy,

        @NotBlank
        @Size(max = 100)
        String requestId,

        @NotNull
        BillingLedgerSourceChannel sourceChannel

) implements Serializable {
}
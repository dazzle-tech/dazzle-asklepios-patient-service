package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record CollectInvoiceBalanceRequest(

        @DecimalMin(value = "0.0001")
        BigDecimal amount,

        @NotBlank
        String paymentMethodCode,

        @NotNull
        Long paymentMethodId,

        @NotBlank
        String requestId,

        String notes

) implements Serializable {
}

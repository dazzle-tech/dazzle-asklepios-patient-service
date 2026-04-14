package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.enumeration.BillingInvoiceStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingInvoiceCreateDTO(
        Long patientId,
        @NotNull Long facilityId,
        BillingInvoiceStatus status,
        @NotNull BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        Currency currency
) implements Serializable {
}

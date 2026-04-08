package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * View Model for creating a BillingInvoiceItem via REST.
 */
public record BillingInvoiceItemCreateDTO(
        @NotNull Long invoiceId,
        Long nurseServiceProductId,
        String code,
        @NotNull BigDecimal quantity,
        @NotNull BigDecimal unitPrice,
        @NotNull BigDecimal totalPrice,
        Currency currency
) implements Serializable {
}
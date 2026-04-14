package com.dazzle.asklepios.service.dto;


import com.dazzle.asklepios.domain.BillingInvoiceItem;
import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * View Model for updating a BillingInvoiceItem via REST.
 */
public record BillingInvoiceItemUpdateDTO(
        @NotNull Long id,
        Long invoiceId,
        Long nurseServiceProductId,
        String code,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Currency currency
) implements Serializable {

    public static BillingInvoiceItemUpdateDTO ofEntity(BillingInvoiceItem item) {
        return new BillingInvoiceItemUpdateDTO(
                item.getId(),
                item.getInvoice() != null ? item.getInvoice().getId() : null,
                item.getNurseServiceProductId(),
                item.getCode(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getCurrency()
        );
    }
}

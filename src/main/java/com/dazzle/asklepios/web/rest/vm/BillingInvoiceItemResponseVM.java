package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.BillingInvoiceItem;
import com.dazzle.asklepios.domain.enumeration.Currency;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingInvoiceItemResponseVM(
        Long id,
        Long nurseServiceProductId,
        String code,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Currency currency
) implements Serializable {

    public static BillingInvoiceItemResponseVM ofEntity(BillingInvoiceItem item) {
        return new BillingInvoiceItemResponseVM(
                item.getId(),
                item.getNurseServiceProductId(),
                item.getCode(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getCurrency()
        );
    }
}

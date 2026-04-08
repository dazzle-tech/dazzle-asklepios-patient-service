package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.BillingInvoice;
import com.dazzle.asklepios.domain.enumeration.BillingInvoiceStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record BillingInvoiceUpdateDTO(
        @NotNull Long id,
        Long patientId,
        Long facilityId,
        BillingInvoiceStatus status,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        Currency currency
) implements Serializable {

    public static BillingInvoiceUpdateDTO ofEntity(BillingInvoice invoice) {
        return new BillingInvoiceUpdateDTO(
                invoice.getId(),
                invoice.getPatientId(),
                invoice.getFacilityId(),
                invoice.getStatus(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getBalanceAmount(),
                invoice.getCurrency()
        );
    }
}

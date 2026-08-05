package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;

public record EncounterInvoiceBalance(

        Long invoiceId,

        String invoiceNumber,

        BigDecimal totalAmount,

        BigDecimal paidAmount,

        BigDecimal outstandingAmount

) implements Serializable {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public static EncounterInvoiceBalance empty() {
        return new EncounterInvoiceBalance(
                null,
                null,
                ZERO,
                ZERO,
                ZERO
        );
    }
}

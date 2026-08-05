package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;

public record SyncInvoicePaymentsResult(

        Long invoiceId,

        BigDecimal paidAmount,

        BigDecimal outstandingAmount

) implements Serializable {
}

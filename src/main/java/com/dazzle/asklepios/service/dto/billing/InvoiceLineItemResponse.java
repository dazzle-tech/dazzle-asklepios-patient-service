package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentItemAdjustmentAction;

import java.io.Serializable;
import java.math.BigDecimal;

public record InvoiceLineItemResponse(

        Long id,

        Long patientServiceProductId,

        Long chargeLineId,

        String itemCode,

        String itemDescription,

        Long quantity,

        BigDecimal unitPrice,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        BigDecimal paidAmount,

        BigDecimal remainingAmount,

        String status,

        Currency currency

) implements Serializable {
}

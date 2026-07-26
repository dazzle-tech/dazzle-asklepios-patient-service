package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record InvoiceAdjustmentSummaryResponse(

        Long invoiceId,

        String documentNumber,

        FinancialDocumentSubtype documentSubtype,

        FinancialDocumentStatus status,

        BigDecimal invoiceTotal,

        BigDecimal totalCreditNotes,

        BigDecimal totalDebitNotes,

        BigDecimal totalPaid,

        BigDecimal outstandingBalance,

        Currency currency,

        List<FinancialDocumentAdjustmentResponse> adjustments

) implements Serializable {
}

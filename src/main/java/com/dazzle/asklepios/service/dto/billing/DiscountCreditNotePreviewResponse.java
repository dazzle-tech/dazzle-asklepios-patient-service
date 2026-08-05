package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.DiscountCreditScope;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record DiscountCreditNotePreviewResponse(

        Long invoiceId,

        FinancialDocumentSubtype invoiceSubtype,

        DiscountCreditScope scope,

        Currency currency,

        BigDecimal outstandingBefore,

        BigDecimal totalDiscount,

        BigDecimal outstandingAfter,

        List<DiscountCreditNoteLinePreview> lines

) implements Serializable {
}

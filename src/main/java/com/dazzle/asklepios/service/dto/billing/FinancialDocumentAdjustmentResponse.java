package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FinancialDocumentAdjustmentResponse(

        Long id,

        String documentNumber,

        FinancialDocumentType documentType,

        FinancialDocumentSubtype documentSubtype,

        FinancialDocumentStatus status,

        Long parentDocumentId,

        BigDecimal totalAmount,

        Currency currency,

        String adjustmentReason,

        Instant createdDate,

        List<FinancialDocumentAdjustmentItemResponse> items

) implements Serializable {
}

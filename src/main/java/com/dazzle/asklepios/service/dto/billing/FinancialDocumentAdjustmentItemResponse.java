package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentItemAdjustmentAction;

import java.io.Serializable;
import java.math.BigDecimal;

public record FinancialDocumentAdjustmentItemResponse(

        Long id,

        FinancialDocumentItemAdjustmentAction adjustmentAction,

        Long parentDocumentItemId,

        Long chargeLineId,

        String itemCode,

        String itemDescription,

        Long quantity,

        BigDecimal unitPrice,

        BigDecimal netAmount,

        Currency currency

) implements Serializable {
}

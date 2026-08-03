package com.dazzle.asklepios.integration.waseel.dto.claim;

import java.math.BigDecimal;

public record ClaimTrackingItemResponse(
        Long id,
        Integer sequence,
        Long patientServiceProductId,
        Long financialDocumentItemId,
        Long billingChargeLineId,
        String itemType,
        String itemCode,
        String itemDescription,
        String invoiceNo,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal net,
        BigDecimal patientShare,
        BigDecimal payerShare
) {}

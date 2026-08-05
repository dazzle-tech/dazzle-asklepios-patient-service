package com.dazzle.asklepios.integration.waseel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PreAuthorizationTrackingItemResponse(
        Long id,
        Integer sequence,
        String itemType,
        String itemCode,
        String itemDescription,
        String nonStandardCode,
        String nonStandardDesc,
        Boolean isPackage,
        Boolean isMaternity,
        BigDecimal quantity,
        String quantityCode,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal factor,
        BigDecimal taxPercent,
        BigDecimal tax,
        BigDecimal patientSharePercent,
        BigDecimal patientShare,
        BigDecimal payerShare,
        BigDecimal net,
        LocalDate startDate,
        LocalDate endDate,
        Long waseelItemId,
        String itemDecision,
        String reasonCodes
) {}

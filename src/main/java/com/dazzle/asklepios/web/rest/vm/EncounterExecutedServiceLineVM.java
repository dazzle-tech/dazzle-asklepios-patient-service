package com.dazzle.asklepios.web.rest.vm;

import java.math.BigDecimal;
import java.time.Instant;

public record EncounterExecutedServiceLineVM(
        Long id,
        boolean cancelled,
        String paymentStatus,
        Instant chargedAt,
        String source,
        String type,
        String code,
        String item,
        BigDecimal quantity,
        String priceSource,
        BigDecimal unitPrice,
        BigDecimal netAmount,
        BigDecimal patientShareAmount,
        BigDecimal insuranceShareAmount,
        String currency,
        BigDecimal grossAmount,
        BigDecimal patientPaidAmount
) {
}

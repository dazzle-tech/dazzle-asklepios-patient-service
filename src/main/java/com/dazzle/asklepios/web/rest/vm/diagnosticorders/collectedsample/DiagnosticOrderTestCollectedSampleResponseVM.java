package com.dazzle.asklepios.web.rest.vm.diagnosticorders.collectedsample;

import com.dazzle.asklepios.domain.DiagnosticOrderTestCollectedSample;

import java.math.BigDecimal;
import java.time.Instant;

public record DiagnosticOrderTestCollectedSampleResponseVM(
        Long id,
        Long orderId,
        Long orderTestId,
        String unit,
        BigDecimal quantity,
        Instant collectedAt
) {
    public static DiagnosticOrderTestCollectedSampleResponseVM ofEntity(DiagnosticOrderTestCollectedSample e) {
        return new DiagnosticOrderTestCollectedSampleResponseVM(
                e.getId(),
                e.getOrderId(),
                e.getOrderTestId(),
                e.getUnit(),
                e.getQuantity(),
                e.getCollectedAt()
        );
    }
}

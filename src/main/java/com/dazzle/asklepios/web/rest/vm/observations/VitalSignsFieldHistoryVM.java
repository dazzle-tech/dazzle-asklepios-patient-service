package com.dazzle.asklepios.web.rest.vm.observations;

import java.time.Instant;

public record VitalSignsFieldHistoryVM(
        String fieldName,
        String oldValue,
        String newValue,
        String changedBy,
        Instant changedAt
) {
}

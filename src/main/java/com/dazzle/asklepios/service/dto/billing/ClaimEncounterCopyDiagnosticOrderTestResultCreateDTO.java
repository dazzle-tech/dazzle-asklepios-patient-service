package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.TestResultType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ClaimEncounterCopyDiagnosticOrderTestResultCreateDTO(
        @NotNull Long orderTestId,
        BigDecimal resultValueNumber,
        String resultValueText,
        TestResultType resultTypeAtEntry
) {
}

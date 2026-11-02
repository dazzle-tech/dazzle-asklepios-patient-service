package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.TestResultType;

import java.math.BigDecimal;

public record ClaimEncounterCopyDiagnosticOrderTestResultCreateDTO(
        Long orderTestId,
        Long patientServiceProductId,
        Long profileTestId,
        BigDecimal resultValueNumber,
        String resultValueText,
        TestResultType resultTypeAtEntry
) {
}

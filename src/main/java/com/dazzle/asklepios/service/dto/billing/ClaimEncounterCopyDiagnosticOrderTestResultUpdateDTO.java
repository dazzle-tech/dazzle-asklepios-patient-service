package com.dazzle.asklepios.service.dto.billing;

import java.math.BigDecimal;

public record ClaimEncounterCopyDiagnosticOrderTestResultUpdateDTO(
        BigDecimal resultValueNumber,
        String resultValueText
) {
}

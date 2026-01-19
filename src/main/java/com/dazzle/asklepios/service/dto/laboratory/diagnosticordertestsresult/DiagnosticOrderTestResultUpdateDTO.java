package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record DiagnosticOrderTestResultUpdateDTO(
        @NotNull Long id,
        @NotNull Long orderId,
        @NotNull Long orderTestId,
        @NotNull Long profileTestId,
        BigDecimal resultValueNumber,
        String resultValueText,
        String marker,
        String normalRangeValue
) implements Serializable {}
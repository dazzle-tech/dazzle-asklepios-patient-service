package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
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
        TestResultMarker marker,
        String normalRangeValue
) implements Serializable {}
package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record DiagnosticOrderTestCollectedSampleDTO(
        @NotNull Long orderId,
        @NotNull Long orderTestId,
        String unit,
         BigDecimal quantity,
        @NotNull Instant collectedAt,
        @NotNull Instant expiryDate,
        @NotBlank String sourceOfSample
) {}
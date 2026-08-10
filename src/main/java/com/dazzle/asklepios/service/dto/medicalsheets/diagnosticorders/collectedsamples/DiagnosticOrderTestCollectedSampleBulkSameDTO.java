package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DiagnosticOrderTestCollectedSampleBulkSameDTO(
        @NotNull Long orderId,
        @NotEmpty List<@NotNull Long> orderTestIds,
         String unit,
         BigDecimal quantity,
        @NotNull Instant collectedAt,
        @NotNull Instant expiryDate,
        @NotBlank String sourceOfSample
) {}

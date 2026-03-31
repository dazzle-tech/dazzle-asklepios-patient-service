package com.dazzle.asklepios.service.dto.availabilityGenerationBatch;

import com.dazzle.asklepios.domain.enumeration.AvailabilityGenerationScope;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public record AvailabilityGenerationBatchApplyDTO(
        @NotNull Long templateId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull Boolean deferred,
        Instant deferredAt,
        @NotNull AvailabilityGenerationScope scope
) {
}

package com.dazzle.asklepios.service.dto.availabilityGenerationBatch;

import com.dazzle.asklepios.domain.enumeration.AvailabilityGenerationScope;
import com.dazzle.asklepios.domain.enumeration.HolidayHandlingMode;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public record AvailabilityGenerationBatchApplyDTO(
        @NotNull Long templateId,
        @NotNull @FutureOrPresent Instant startDate,
        @NotNull @FutureOrPresent Instant endDate,
        @NotNull Boolean deferred,
        Instant deferredAt,
        @NotNull AvailabilityGenerationScope scope,
        HolidayHandlingMode holidayHandlingMode

) {
}

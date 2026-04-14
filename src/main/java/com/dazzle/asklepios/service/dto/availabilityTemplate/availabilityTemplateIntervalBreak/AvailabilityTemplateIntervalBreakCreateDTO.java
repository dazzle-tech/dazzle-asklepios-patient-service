package com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateIntervalBreak;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalTime;

public record AvailabilityTemplateIntervalBreakCreateDTO(
        @NotNull
        Long intervalId,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime
) implements Serializable {}
package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record PatientWarningUpdateDTO(
        @NotNull Long id,
        @NotNull String warningType,
        @NotNull @NotBlank String warning,
        @NotNull Severity severity,
        boolean onsetDateUndefined,
        Instant onsetDate,
        boolean byPatient,
        String sourceOfInformation,
        String note,
        String actionTaken
) implements Serializable {}
package com.dazzle.asklepios.service.dto.dentalChart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record PlannedTreatmentReturnDTO(
        @NotNull Long encounterId,
        @NotBlank(message = "Reason is required.") String reason
) implements Serializable {}

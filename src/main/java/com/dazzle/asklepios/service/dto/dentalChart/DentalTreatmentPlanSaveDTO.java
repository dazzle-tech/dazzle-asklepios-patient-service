package com.dazzle.asklepios.service.dto.dentalChart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DentalTreatmentPlanSaveDTO(
        @NotNull Long patientId,
        @NotNull Long originEncounterId,
        @NotBlank String title,
        String notes,
        Long practitionerId
) {}

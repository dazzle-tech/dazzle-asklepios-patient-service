package com.dazzle.asklepios.service.dto.dentalChart;

import jakarta.validation.constraints.NotBlank;

public record DentalTreatmentPlanReasonDTO(@NotBlank String reason) {}

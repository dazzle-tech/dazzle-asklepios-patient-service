package com.dazzle.asklepios.service.dto.dentalChart;

import jakarta.validation.constraints.NotNull;

public record PlannedTreatmentExecutionDTO(@NotNull Long executionEncounterId) {}

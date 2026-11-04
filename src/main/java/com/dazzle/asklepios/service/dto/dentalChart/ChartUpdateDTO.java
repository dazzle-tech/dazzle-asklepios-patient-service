package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record ChartUpdateDTO(
        @NotNull Instant chartDate,
        @NotNull DentalChartType chartType,
        @NotNull DentalChartStatus status,
        String treatmentPlan
) implements Serializable {}

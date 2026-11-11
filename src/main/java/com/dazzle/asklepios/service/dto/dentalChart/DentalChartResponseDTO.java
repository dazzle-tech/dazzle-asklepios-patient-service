package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
import com.dazzle.asklepios.domain.enumeration.ToothNumberingSystem;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record DentalChartResponseDTO(
        Long id,
        Long patientId,
        Long encounterId,
        Instant chartDate,
        DentalChartType chartType,
        DentalChartStatus status,
        String treatmentPlan,
        ToothNumberingSystem numberingSystem,
        Long ageRuleId,
        List<DentalChartToothDTO> teeth,
        List<ToothActionDTO> chartActions,
        List<DentalChartSummaryDTO> historyCharts
) implements Serializable {}

package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
import com.dazzle.asklepios.domain.enumeration.ToothNumberingSystem;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        Long numberingMethodId,
        Map<String, String> numberingLabels,
        Long version,
        List<DentalChartToothDTO> teeth,
        List<DentalChartToothDTO> inactiveTeeth,
        List<ToothActionDTO> chartActions,
        List<DentalChartSummaryDTO> historyCharts
) implements Serializable {}

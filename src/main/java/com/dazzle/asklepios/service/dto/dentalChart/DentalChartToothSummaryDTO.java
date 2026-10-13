package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.enumeration.ToothNumber;

import java.io.Serializable;

public record DentalChartToothSummaryDTO(
        Long id,
        Long chartId,
        ToothNumber toothNumber,
        boolean missing
) implements Serializable {

    public static DentalChartToothSummaryDTO fromEntity(DentalChartTooth entity) {
        return new DentalChartToothSummaryDTO(
                entity.getId(),
                entity.getChartId(),
                entity.getToothNumber(),
                entity.isMissing()
        );
    }
}

package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;

import java.io.Serializable;
import java.time.Instant;

public record DentalChartSummaryDTO(
        Long id,
        Long patientId,
        Long encounterId,
        Instant chartDate,
        DentalChartType chartType,
        DentalChartStatus status
) implements Serializable {

    public static DentalChartSummaryDTO fromEntity(DentalChart entity) {
        return new DentalChartSummaryDTO(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getEncounter().getId(),
                entity.getChartDate(),
                entity.getChartType(),
                entity.getStatus()
        );
    }
}

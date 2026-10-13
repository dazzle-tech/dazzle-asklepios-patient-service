package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;

import java.io.Serializable;
import java.math.BigDecimal;

public record PlannedTreatmentDTO(
        Long id,
        Long patientId,
        Long encounterId,
        PlannedTreatmentType type,
        Integer visitNumber,
        Long cdtCodeId,
        Long toothId,
        String note,
        ToothSurface surface,
        BigDecimal insurance,
        ServiceSource source,
        Long toothActionId,
        boolean cancelled,
        String cancelledBy,
        String cancellationReason,
        DentalChartToothSummaryDTO tooth
) implements Serializable {

    public static PlannedTreatmentDTO fromEntity(DentalPlannedTreatment entity, DentalChartToothSummaryDTO tooth) {
        return new PlannedTreatmentDTO(
                entity.getId(),
                entity.getPatientId(),
                entity.getEncounterId(),
                entity.getType(),
                entity.getVisitNumber(),
                entity.getCdtCodeId(),
                entity.getToothId(),
                entity.getNote(),
                entity.getSurface(),
                entity.getInsurance(),
                entity.getSource(),
                entity.getToothActionId(),
                entity.isCancelled(),
                entity.getCancelledBy(),
                entity.getCancellationReason(),
                tooth
        );
    }
}

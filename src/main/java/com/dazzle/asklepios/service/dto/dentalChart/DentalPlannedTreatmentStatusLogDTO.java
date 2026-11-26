package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalPlannedTreatmentStatusLog;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentHistoryAction;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;

import java.time.Instant;

public record DentalPlannedTreatmentStatusLogDTO(
        Long id,
        Long plannedTreatmentId,
        Long treatmentPlanId,
        Long toothId,
        String toothLabel,
        PlannedTreatmentHistoryAction action,
        PlannedTreatmentStatus previousStatus,
        PlannedTreatmentStatus newStatus,
        String reason,
        String changedBy,
        Instant changedAt
) {
    public static DentalPlannedTreatmentStatusLogDTO from(DentalPlannedTreatmentStatusLog log) {
        return new DentalPlannedTreatmentStatusLogDTO(
                log.getId(),
                log.getPlannedTreatmentId(),
                log.getTreatmentPlanId(),
                log.getToothId(),
                log.getToothLabel(),
                log.getAction(),
                log.getPreviousStatus(),
                log.getNewStatus(),
                log.getReason(),
                log.getChangedBy(),
                log.getChangedAt()
        );
    }
}

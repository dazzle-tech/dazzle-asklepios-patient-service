package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalTreatmentPlanStatusLog;
import com.dazzle.asklepios.domain.enumeration.DentalTreatmentPlanStatus;

import java.time.Instant;

public record DentalTreatmentPlanStatusLogDTO(
        Long id,
        Long treatmentPlanId,
        DentalTreatmentPlanStatus previousStatus,
        DentalTreatmentPlanStatus newStatus,
        String reason,
        String changedBy,
        Instant changedAt
) {
    public static DentalTreatmentPlanStatusLogDTO from(DentalTreatmentPlanStatusLog log) {
        return new DentalTreatmentPlanStatusLogDTO(
                log.getId(),
                log.getTreatmentPlanId(),
                log.getPreviousStatus(),
                log.getNewStatus(),
                log.getReason(),
                log.getChangedBy(),
                log.getChangedAt()
        );
    }
}

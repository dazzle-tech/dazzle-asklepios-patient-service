package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.DentalTreatmentPlanStatus;

import java.time.Instant;

public record DentalTreatmentPlanSummaryDTO(
        Long id,
        String planNumber,
        Long patientId,
        Long originEncounterId,
        String originEncounterNumber,
        String title,
        String notes,
        Long practitionerId,
        DentalTreatmentPlanStatus status,
        DentalTreatmentPlanStatus resumeStatus,
        String holdReason,
        String cancellationReason,
        Instant createdDate,
        Instant completedAt,
        String completedBy,
        int completedTreatments,
        int activeTreatments
) {}

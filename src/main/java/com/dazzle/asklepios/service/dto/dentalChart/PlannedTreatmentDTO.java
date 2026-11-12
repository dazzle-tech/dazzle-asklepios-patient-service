package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.domain.enumeration.TreatmentScope;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Unified treatment-plan row. Persisted planned treatments use a positive {@code id}.
 * Chart-only existing treatments are projected documentation rows: {@code id} is null,
 * {@code existing} is true, {@code status} is COMPLETED, and no charge link is present.
 */
public record PlannedTreatmentDTO(
        Long id,
        Long patientId,
        Long encounterId,
        PlannedTreatmentType type,
        Integer visitNumber,
        Long cdtCodeId,
        Long toothId,
        TreatmentScope treatmentScope,
        List<Long> coveredToothIds,
        String note,
        ToothSurface surface,
        BigDecimal insurance,
        Long serviceId,
        Long procedureId,
        BigDecimal price,
        Long quantity,
        BigDecimal discount,
        PlannedTreatmentStatus status,
        Long patientServiceAndProductId,
        ServiceSource source,
        Long toothActionId,
        boolean cancelled,
        String cancelledBy,
        String cancellationReason,
        DentalChartToothSummaryDTO tooth,
        boolean existing,
        String dentalActionDescription,
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial,
        String completedBy,
        Instant completedAt,
        Long treatmentPlanId,
        Long treatmentSessionId,
        Long executionEncounterId
) implements Serializable {

    public static PlannedTreatmentDTO fromEntity(DentalPlannedTreatment entity, DentalChartToothSummaryDTO tooth) {
        PlannedTreatmentClinicalDetails clinical =
                PlannedTreatmentClinicalDetails.fromJson(entity.getClinicalDetails());
        TreatmentScope scope = entity.getTreatmentScope() != null
                ? entity.getTreatmentScope()
                : TreatmentScope.SPECIFIC_TOOTH;
        return new PlannedTreatmentDTO(
                entity.getId(),
                entity.getPatientId(),
                entity.getEncounterId(),
                entity.getType(),
                entity.getVisitNumber(),
                entity.getCdtCodeId(),
                entity.getToothId(),
                scope,
                parseCoveredToothIds(entity.getCoveredToothIds()),
                entity.getNote(),
                entity.getSurface(),
                entity.getInsurance(),
                entity.getServiceId(),
                entity.getProcedureId(),
                entity.getPrice(),
                entity.getQuantity(),
                entity.getDiscount(),
                entity.getStatus() != null ? entity.getStatus() : PlannedTreatmentStatus.PENDING,
                entity.getPatientServiceAndProduct() != null ? entity.getPatientServiceAndProduct().getId() : null,
                entity.getSource(),
                entity.getToothActionId(),
                entity.isCancelled(),
                entity.getCancelledBy(),
                entity.getCancellationReason(),
                tooth,
                false,
                null,
                clinical.anesthesiaUsed(),
                clinical.dose(),
                clinical.unit(),
                clinical.fillingMaterial(),
                entity.getCompletedBy(),
                entity.getCompletedAt(),
                entity.getTreatmentPlan() != null ? entity.getTreatmentPlan().getId() : null,
                entity.getTreatmentSession() != null ? entity.getTreatmentSession().getId() : null,
                entity.getExecutionEncounter() != null ? entity.getExecutionEncounter().getId() : null
        );
    }

    public static List<Long> parseCoveredToothIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static String encodeCoveredToothIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}

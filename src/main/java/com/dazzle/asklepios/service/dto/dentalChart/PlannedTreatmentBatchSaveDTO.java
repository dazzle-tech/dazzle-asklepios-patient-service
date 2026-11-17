package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Batch Add for Specific Teeth: one planned-treatment row per tooth,
 * sharing a single Session Number (visitNumber) allocated once for the batch.
 * Clinical snapshot values are copied onto every row.
 */
public record PlannedTreatmentBatchSaveDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull PlannedTreatmentType type,
        Integer visitNumber,
        @NotNull Long cdtCodeId,
        @NotEmpty List<Long> toothIds,
        String note,
        ToothSurface surface,
        BigDecimal insurance,
        Long serviceId,
        Long procedureId,
        BigDecimal price,
        Long quantity,
        BigDecimal discount,
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial,
        Long treatmentPlanId,
        Long treatmentSessionId,
        String revisionReason
) implements Serializable {}

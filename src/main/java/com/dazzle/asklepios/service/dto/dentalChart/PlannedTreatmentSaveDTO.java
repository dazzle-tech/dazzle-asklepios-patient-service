package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.domain.enumeration.TreatmentScope;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record PlannedTreatmentSaveDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull PlannedTreatmentType type,
        Integer visitNumber,
        @NotNull Long cdtCodeId,
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
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial,
        Long treatmentPlanId,
        Long treatmentSessionId,
        String revisionReason
) implements Serializable {}

package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record PlannedTreatmentSaveDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull PlannedTreatmentType type,
        Integer visitNumber,
        @NotNull Long cdtCodeId,
        Long toothId,
        String note,
        ToothSurface surface,
        BigDecimal fees,
        BigDecimal insurance,
        BigDecimal discount
) implements Serializable {}

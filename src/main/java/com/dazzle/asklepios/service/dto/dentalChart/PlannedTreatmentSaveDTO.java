package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentDiscountType;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.domain.enumeration.TreatmentScope;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record PlannedTreatmentSaveDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull PlannedTreatmentType type,
        Integer visitNumber,
        Long cdtCodeId,
        Long toothId,
        TreatmentScope treatmentScope,
        List<Long> coveredToothIds,
        String note,
        @JsonAlias("surface")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<ToothSurface> surfaces,
        BigDecimal insurance,
        Long serviceId,
        Long procedureId,
        BigDecimal price,
        Long quantity,
        BigDecimal discount,
        PlannedTreatmentDiscountType discountType,
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial,
        Long treatmentPlanId,
        Long treatmentSessionId,
        String revisionReason
) implements Serializable {}

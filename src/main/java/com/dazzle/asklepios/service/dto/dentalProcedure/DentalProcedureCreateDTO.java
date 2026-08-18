package com.dazzle.asklepios.service.dto.dentalProcedure;

import com.dazzle.asklepios.domain.enumeration.ToothNumber;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record DentalProcedureCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull ToothNumber toothNumber,
        @NotNull String surface,
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial,
        @NotNull Long procedureId,
        Long serviceId,
        Long cdtCodeId,
        String notes,
        Boolean acceptUncoveredAsCash
) implements Serializable {}
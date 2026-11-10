package com.dazzle.asklepios.service.dto.dentalProcedure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record DentalProcedureUpdateDTO(
        @NotNull Long id,
        @NotEmpty List<@NotBlank String> toothNumbers,
        @NotNull String surface,
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial,
        @NotNull Long procedureId,
        Long serviceId,
        Long cdtCodeId,
        String notes,
        Boolean informedConsentObtained,
        String treatmentOutcome
) implements Serializable {}
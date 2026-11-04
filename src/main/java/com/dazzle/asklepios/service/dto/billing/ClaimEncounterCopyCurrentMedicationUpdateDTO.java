package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Date;

public record ClaimEncounterCopyCurrentMedicationUpdateDTO(
        Long activeIngredientId,
        BigDecimal dosage,
        String unit,
        String frequency,
        Date startDate,
        @NotNull Boolean patientIsFree,
        String freeText
) {}
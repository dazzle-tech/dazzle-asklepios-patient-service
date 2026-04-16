package com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders;

import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import jakarta.validation.constraints.NotNull;

public record UrgentCareMedicationOrderUpdateDTO(
        @NotNull Long id,
        @NotNull Long activeIngredientId,
        @NotNull MedicationInstructionType instructionType,
        String instructionText,
        Long dose,
        String doseUnit,
        String route,
        String frequency
) {
}

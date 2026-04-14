package com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders;

import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import jakarta.validation.constraints.NotNull;

public record PatientUccMedicationOrderUpdateDTO(
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

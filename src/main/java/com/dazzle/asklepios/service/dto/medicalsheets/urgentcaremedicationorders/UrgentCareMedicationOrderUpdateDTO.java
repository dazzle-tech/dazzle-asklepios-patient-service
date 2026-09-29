package com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders;

import com.dazzle.asklepios.domain.enumeration.FrequencyList;
import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.domain.enumeration.Unit;
import jakarta.validation.constraints.NotNull;

public record UrgentCareMedicationOrderUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long activeIngredientId,

        @NotNull
        MedicationInstructionType instructionType,

        String instructionText,

        Long dose,
        String doseUnit,
        String route,

        Integer frequencyNumber,
        FrequencyList frequencyUnit,
        Integer duration,
        java.time.LocalTime startTime
) {
}

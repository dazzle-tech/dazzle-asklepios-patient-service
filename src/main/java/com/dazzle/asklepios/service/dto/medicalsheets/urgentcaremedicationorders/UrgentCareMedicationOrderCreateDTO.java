package com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders;

import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.domain.enumeration.Unit;
import com.dazzle.asklepios.service.validation.ValidMedicationInstruction;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@ValidMedicationInstruction
public record UrgentCareMedicationOrderCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        Long activeIngredientId,

        @NotNull
        MedicationInstructionType instructionType,

        String instructionText,

        Long dose,
        String doseUnit,
        String route,

        Integer frequencyNumber,
        Unit frequencyUnit,
        Integer duration,
        java.time.LocalTime startTime
) {
}

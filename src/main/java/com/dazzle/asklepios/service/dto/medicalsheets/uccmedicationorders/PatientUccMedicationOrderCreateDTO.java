package com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders;


import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.service.validation.ValidMedicationInstruction;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@ValidMedicationInstruction
public record PatientUccMedicationOrderCreateDTO(



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
        String frequency
) {
}
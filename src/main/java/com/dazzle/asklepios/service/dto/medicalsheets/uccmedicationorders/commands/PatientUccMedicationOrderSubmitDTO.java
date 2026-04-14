package com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.commands;

import jakarta.validation.constraints.NotNull;

public record PatientUccMedicationOrderSubmitDTO(
        @NotNull
        Boolean isHighAlert
) {
}

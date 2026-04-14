package com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.commands;


import jakarta.validation.constraints.NotBlank;

public record PatientUccMedicationOrderDiscardDTO(
        @NotBlank String discardReason
) {
}
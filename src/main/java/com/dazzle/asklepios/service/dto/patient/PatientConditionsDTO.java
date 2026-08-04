package com.dazzle.asklepios.service.dto.patient;

import jakarta.validation.constraints.NotBlank;

public record PatientConditionsDTO(
        @NotBlank String patientConditions
) {
}
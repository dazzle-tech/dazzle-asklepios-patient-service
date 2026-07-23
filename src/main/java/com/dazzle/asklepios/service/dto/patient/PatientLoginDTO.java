package com.dazzle.asklepios.service.dto.patient;

import jakarta.validation.constraints.NotBlank;


public record PatientLoginDTO(
        @NotBlank
        String medicalRecordNumber,

        @NotBlank
        String password,

        Boolean rememberMe
) {
}

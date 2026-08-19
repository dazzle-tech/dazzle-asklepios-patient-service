package com.dazzle.asklepios.service.dto.patientPortal;

import jakarta.validation.constraints.NotBlank;

public record PatientOtpRequestDTO(

        @NotBlank(message = "Primary document number is required")
        String primaryDocumentNumber

) {
}
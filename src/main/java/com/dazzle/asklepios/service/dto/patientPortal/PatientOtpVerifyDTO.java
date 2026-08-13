package com.dazzle.asklepios.service.dto.patientPortal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PatientOtpVerifyDTO(

        @NotBlank(message = "Primary document number is required")
        String primaryDocumentNumber,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
        String otp
) {
}
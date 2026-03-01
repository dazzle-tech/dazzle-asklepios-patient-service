package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.sendtest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExternalTestDTO(
        @NotNull Long testId,
        @NotBlank String facilityName,
        @NotBlank String reason
) {
}


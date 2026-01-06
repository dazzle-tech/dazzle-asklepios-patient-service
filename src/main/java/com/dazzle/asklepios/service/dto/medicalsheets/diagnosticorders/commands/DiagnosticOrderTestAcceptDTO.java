package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.commands;

import jakarta.validation.constraints.NotBlank;

public record DiagnosticOrderTestAcceptDTO(
        @NotBlank String acceptedBy
) { }

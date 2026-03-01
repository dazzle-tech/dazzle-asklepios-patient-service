package com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.commands;

import jakarta.validation.constraints.NotBlank;

public record DiagnosticTestRequestRejectDTO(
        @NotBlank String rejectedReason
) {}

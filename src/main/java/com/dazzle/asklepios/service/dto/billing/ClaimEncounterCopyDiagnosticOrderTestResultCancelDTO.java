package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotBlank;

public record ClaimEncounterCopyDiagnosticOrderTestResultCancelDTO(
        @NotBlank String cancellationReason
) {
}

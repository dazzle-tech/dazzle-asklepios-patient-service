package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotBlank;

public record ClaimEncounterCopyCurrentMedicationCancelDTO(
        @NotBlank String cancellationReason
) {}
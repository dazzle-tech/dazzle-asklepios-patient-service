package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotBlank;

public record ClaimEncounterCopyHospitalizationCancelDTO(
        @NotBlank String cancellationReason
) {}
package com.dazzle.asklepios.service.dto.progressNotes;

import jakarta.validation.constraints.NotBlank;

public record ClaimEncounterProgressNoteCancelDTO(
        @NotBlank String cancellationReason
) {}
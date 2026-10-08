package com.dazzle.asklepios.service.dto.progressNotes;

import jakarta.validation.constraints.NotBlank;

public record ClaimEncounterProgressNoteUpdateDTO(
        @NotBlank String noteText
) {}
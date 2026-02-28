package com.dazzle.asklepios.service.dto.progressNotes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ProgressNoteCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotBlank
        String noteText

) implements Serializable {
}

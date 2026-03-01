package com.dazzle.asklepios.service.dto.progressNotes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ProgressNoteUpdateDTO(

        @NotNull
        Long id,

        @NotBlank
        String noteText

) implements Serializable {
}

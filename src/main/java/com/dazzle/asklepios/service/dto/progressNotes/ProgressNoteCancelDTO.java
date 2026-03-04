package com.dazzle.asklepios.service.dto.progressNotes;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record ProgressNoteCancelDTO(

        @NotBlank
        String cancellationReason

) implements Serializable {
}
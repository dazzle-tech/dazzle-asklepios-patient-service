package com.dazzle.asklepios.service.dto.Hospitalizations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HospitalizationCancelDTO(

        @NotNull
        Long id,

        @NotBlank
        String cancellationReason

) {
}
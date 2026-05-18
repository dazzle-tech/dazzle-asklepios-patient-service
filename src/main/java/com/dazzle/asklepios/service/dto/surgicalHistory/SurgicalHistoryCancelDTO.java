package com.dazzle.asklepios.service.dto.surgicalHistory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SurgicalHistoryCancelDTO(

        @NotNull
        Long id,

        @NotBlank
        String cancellationReason

) {
}
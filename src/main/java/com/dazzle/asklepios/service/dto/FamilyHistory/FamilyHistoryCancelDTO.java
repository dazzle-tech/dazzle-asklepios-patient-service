package com.dazzle.asklepios.service.dto.FamilyHistory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FamilyHistoryCancelDTO(

        @NotNull
        Long id,

        @NotBlank
        String cancellationReason

) {
}
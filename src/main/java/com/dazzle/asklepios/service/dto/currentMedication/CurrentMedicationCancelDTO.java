package com.dazzle.asklepios.service.dto.currentMedication;

import jakarta.validation.constraints.NotNull;

public record CurrentMedicationCancelDTO(

        @NotNull
        Long id,

        String cancellationReason

) {
}
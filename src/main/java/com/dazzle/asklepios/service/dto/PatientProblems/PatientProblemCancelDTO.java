package com.dazzle.asklepios.service.dto.PatientProblems;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatientProblemCancelDTO(

        @NotNull
        Long id,

        @NotBlank
        String cancellationReason

) {
}
package com.dazzle.asklepios.service.dto.PatientProblems;

import jakarta.validation.constraints.NotNull;

public record PatientProblemCancelDTO(

        @NotNull
        Long id,

        String cancellationReason

) {
}
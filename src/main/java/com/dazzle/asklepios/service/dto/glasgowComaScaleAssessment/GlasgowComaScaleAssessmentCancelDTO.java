package com.dazzle.asklepios.service.dto.glasgowComaScaleAssessment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record GlasgowComaScaleAssessmentCancelDTO(
        @NotNull
        Long id,

        @NotEmpty
        String cancellationReason
) {
}
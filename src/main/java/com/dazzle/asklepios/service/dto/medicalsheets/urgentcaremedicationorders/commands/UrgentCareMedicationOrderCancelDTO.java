package com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.commands;

import jakarta.validation.constraints.NotBlank;

public record UrgentCareMedicationOrderCancelDTO(
        @NotBlank String cancellationReason
) {
}
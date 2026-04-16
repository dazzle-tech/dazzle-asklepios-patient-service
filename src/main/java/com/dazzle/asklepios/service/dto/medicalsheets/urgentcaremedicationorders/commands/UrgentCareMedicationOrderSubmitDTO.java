package com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.commands;

import jakarta.validation.constraints.NotNull;

public record UrgentCareMedicationOrderSubmitDTO(
        @NotNull
        Boolean isHighAlert
) {
}

package com.dazzle.asklepios.service.dto.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DiagnosticTestAppointmentRescheduleDTO(
        @NotNull Long orderTestId,
        @NotNull Long newAppointmentId,
        @NotBlank String rescheduleReason
) {
}
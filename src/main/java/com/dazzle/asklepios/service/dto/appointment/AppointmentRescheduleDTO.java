package com.dazzle.asklepios.service.dto.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppointmentRescheduleDTO(
        @NotNull Long oldAppointmentId,
        @NotNull Long newAppointmentId,
        @NotBlank String rescheduleReason
) {
}

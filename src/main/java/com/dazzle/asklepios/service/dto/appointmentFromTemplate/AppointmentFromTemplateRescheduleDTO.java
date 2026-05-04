package com.dazzle.asklepios.service.dto.appointmentFromTemplate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppointmentFromTemplateRescheduleDTO(
        @NotNull Long oldAppointmentId,
        @NotNull Long newAppointmentId,
        @NotBlank String rescheduleReason
) {
}

package com.dazzle.asklepios.service.dto.appointmentFromTemplate;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record AppointmentFromTemplateBookPatientDTO(

        @NotNull(message = "appointmentId is required")
        Long id,

        @NotNull(message = "patientId is required")
        Long patientId,

        Long defaultService,

        Long defaultPractitioner,

        String reason,

        AppointmentStatus status,

        String note
) {
}

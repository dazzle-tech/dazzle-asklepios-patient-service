package com.dazzle.asklepios.service.dto.appointmentFromTemplate;

import jakarta.validation.constraints.NotBlank;
import software.amazon.awssdk.annotations.NotNull;

public record AppointmentFromTemplateNoShowDTO(
        @NotNull Long id,
        @NotBlank String noShowReason
) {
}

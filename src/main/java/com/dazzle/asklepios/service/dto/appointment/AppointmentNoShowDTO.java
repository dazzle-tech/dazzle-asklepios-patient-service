package com.dazzle.asklepios.service.dto.appointment;

import jakarta.validation.constraints.NotBlank;
import software.amazon.awssdk.annotations.NotNull;

public record AppointmentNoShowDTO(
        @NotNull Long id,
        @NotBlank String noShowReason
) {
}

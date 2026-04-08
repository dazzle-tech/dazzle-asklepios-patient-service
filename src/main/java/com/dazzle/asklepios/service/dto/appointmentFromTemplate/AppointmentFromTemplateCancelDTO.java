package com.dazzle.asklepios.service.dto.appointmentFromTemplate;

import jakarta.validation.constraints.NotBlank;
import software.amazon.awssdk.annotations.NotNull;

public record AppointmentFromTemplateCancelDTO(
        @NotNull Long id,
        @NotBlank String cancelReason) {


}

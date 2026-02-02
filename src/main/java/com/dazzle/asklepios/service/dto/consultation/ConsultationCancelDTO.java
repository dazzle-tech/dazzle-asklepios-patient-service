package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public record ConsultationCancelDTO(

        @NotBlank(message = "Cancellation reason is required")
        String cancellationReason,

        Long cancelledBy

) implements Serializable {}


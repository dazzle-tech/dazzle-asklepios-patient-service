package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ConsultationCancelDTO(

        @NotBlank(message = "Cancellation reason is required")
        String cancellationReason,

        @NotNull
        Long cancelledBy

) implements Serializable {}


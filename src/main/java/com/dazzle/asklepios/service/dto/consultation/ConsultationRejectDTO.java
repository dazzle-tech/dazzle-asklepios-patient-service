package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ConsultationRejectDTO(

        @NotBlank(message = "Reject reason is required")
        String reason,

        @NotNull
        Long rejectedBy

) implements Serializable {
}

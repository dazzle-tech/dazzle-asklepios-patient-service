package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ConsultationResponseDTO(

        @NotBlank(message = "Response text is required")
        String responseText,

        @NotNull(message = "responseBy is required")
        Long responseBy

) implements Serializable {
}

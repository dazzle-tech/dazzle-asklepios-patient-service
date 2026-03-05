package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record ConsultationResponseDTO(

        @NotBlank(message = "Response text is required")
        String responseText

) implements Serializable {
}
package com.dazzle.asklepios.service.dto.consultation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultationResponseDTO(

        @NotBlank(message = "Response text is required")
        String responseText

) implements Serializable {
}
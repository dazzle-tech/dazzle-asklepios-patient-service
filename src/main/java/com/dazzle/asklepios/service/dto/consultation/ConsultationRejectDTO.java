package com.dazzle.asklepios.service.dto.consultation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultationRejectDTO(

        @NotBlank(message = "Reject reason is required")
        String reason

) implements Serializable {
}
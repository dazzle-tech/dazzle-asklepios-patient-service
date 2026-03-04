package com.dazzle.asklepios.service.dto.telephonicconsultation;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record TelephonicConsultationCancelDTO(

        @NotBlank(message = "Cancel reason is required")
        String reason

) implements Serializable {
}
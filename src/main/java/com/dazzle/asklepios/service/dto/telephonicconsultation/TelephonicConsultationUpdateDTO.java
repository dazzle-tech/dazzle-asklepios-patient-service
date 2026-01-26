package com.dazzle.asklepios.service.dto.telephonicconsultation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record TelephonicConsultationUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long practitionerId,

        @NotNull
        Instant dateOfCall,

        @NotBlank
        String consultationContent,

        Long approvalNumber,

        String notes,

        String extraDocumentation

) implements Serializable {
}


package com.dazzle.asklepios.service.dto.telephonicconsultation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.util.Date;

public record TelephonicConsultationUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long practitionerId,

        @NotNull
        @PastOrPresent
        Date dateOfCall,

        @NotNull
        String consultationContent,

        Integer approvalNumber,

        String notes,

        String extraDocumentation

) implements Serializable {
}

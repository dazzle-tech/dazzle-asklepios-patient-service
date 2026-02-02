package com.dazzle.asklepios.service.dto.consultation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ConsultationUpdateDTO(

        @NotNull
        Long id,

        String destinationType,
        Long toFacilityId,
        Long toDepartmentId,

        String consultantSpeciality,
        Long practitionerId,

        String consultationMethod,
        String consultationType,
        String consultationLevel,

        @NotBlank
        String consultationContent,


        String notes,
        String extraDocument,
        Long approvalNumber

) implements Serializable {}

package com.dazzle.asklepios.service.dto.consultation;

import com.dazzle.asklepios.domain.enumeration.DestinationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ConsultationUpdateDTO(

        @NotNull
        Long id,

        @NotBlank
        DestinationType destinationType,
        @NotNull
        Long toFacilityId,
        Long toDepartmentId,

        String consultantSpeciality,
        Long practitionerId,

        @NotBlank
        String consultationMethod,
        @NotBlank
        String consultationType,
        @NotNull
        String consultationLevel,

        @NotBlank
        String consultationContent,


        String notes,
        String extraDocument,
        Long approvalNumber

) implements Serializable {}

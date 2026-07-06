package com.dazzle.asklepios.service.dto.consultation;

import com.dazzle.asklepios.domain.enumeration.ConsultationType;
import com.dazzle.asklepios.domain.enumeration.DestinationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ConsultationUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        DestinationType destinationType,

        @NotNull
        Long toFacilityId,

        Long toDepartmentId,

        String consultantSpeciality,
        Long practitionerId,

        @NotBlank
        String consultationMethod,

        @NotNull
        ConsultationType consultationType,

        @NotNull
        String consultationLevel,

        @NotBlank
        String consultationContent,

        String notes,
        String extraDocument,
        Long approvalNumber

) implements Serializable {}
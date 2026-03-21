package com.dazzle.asklepios.service.dto.consultation;

import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.domain.enumeration.DestinationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsultationCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        Long fromFacilityId,

        @NotNull
        Long toFacilityId,

        @NotNull
        Long fromDepartmentId,

        Long toDepartmentId,

        @NotBlank
        String consultationType,

        String consultantSpeciality,

        Long practitionerId,

        @NotNull
        DestinationType destinationType,

        @NotBlank
        String consultationMethod,

        @NotBlank
        String consultationLevel,

        @NotBlank
        String consultationContent,

        String notes,
        String extraDocument,
        Long approvalNumber,

        @NotNull
        ConsultationStatus status

) implements Serializable {
}
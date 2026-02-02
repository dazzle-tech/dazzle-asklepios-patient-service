package com.dazzle.asklepios.service.dto.consultation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

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

        @NotBlank
        String destinationType,

        @NotBlank
        String consultationMethod,

        @NotBlank
        String consultationLevel,

        @NotBlank
        String consultationContent,

        String notes,
        String extraDocument,
        Long approvalNumber,

        String status,
        Instant responseDate,
        Long responseBy,
        String responseText,
        Instant rejectedDate,
        Long rejectedBy,
        String rejectReason

) implements Serializable {
}

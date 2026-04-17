package com.dazzle.asklepios.service.dto.appointmentRequest;

import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppointmentRequestCreateDTO (

    @NotNull Long patientId,

    @NotNull Long facilityId,

    @NotNull Long departmentId,

    @NotNull Long sourceEncounterId,

    TemplateType requestedResourceType,

     Long requestedResourceId,

    @NotNull
     EncounterPriority priority,

    @Size(max = 255)
     String reason,

     String note
){

}
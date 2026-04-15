package com.dazzle.asklepios.service.dto.appointmentRequest;

import com.dazzle.asklepios.domain.enumeration.AppointmentRequestStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public record AppointmentRequestUpdateDTO(

    @NotNull Long id,

    @NotNull
     Long patientId,

    @NotNull
     Long facilityId,

    @NotNull
     Long departmentId,

    @NotNull
     Long sourceEncounterId,

     Long appointmentId,

     TemplateType requestedResourceType,

     Long requestedResourceId,


    @NotNull
     EncounterPriority priority,

    @Size(max = 255)
     String reason,

     String note,

    @NotNull
     AppointmentRequestStatus status,

    @Size(max = 255)
     String cancelReason){

}
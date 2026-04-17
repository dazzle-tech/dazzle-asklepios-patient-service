package com.dazzle.asklepios.web.rest.vm.appointmentRequest;

import com.dazzle.asklepios.domain.enumeration.AppointmentRequestStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.TemplateType;

import java.time.Instant;

public record AppointmentRequestResponseVM(

        Long id,

        Long patientId,
        String patientName,
        String patientMrn,

        Long facilityId,
        String facilityName,

        Long departmentId,
        String departmentName,

        Long sourceEncounterId,
        Long appointmentId,

        TemplateType requestedResourceType,
        Long requestedResourceId,

        EncounterPriority priority,
        String reason,
        String note,

        AppointmentRequestStatus status,

        Instant cancelledAt,
        String cancelReason,

        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
)
{

}
package com.dazzle.asklepios.service.dto.appointment;

import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import jakarta.validation.constraints.NotNull;

public record AppointmentQuickAppointmentDTO(
        @NotNull(message = "facilityId is required") Long facilityId,
        @NotNull(message = "departmentId is required") Long departmentId,
        @NotNull(message = "resourceType is required") TemplateType resourceType,
        @NotNull(message = "resourceId is required") Long resourceId,
        @NotNull(message = "patientId is required") Long patientId,
        @NotNull(message = "service is required") EncounterReason service,
        @NotNull(message = "priority is required") EncounterPriority priority,
        Long defaultServiceId,
        Long defaultPractitionerId,
        String reason,
        String note,
        Long followUpEncounterId,
        String originType,
        String originName,
        String hl7AppointmentNumber
) {
}

package com.dazzle.asklepios.service.dto.appointment;

import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AppointmentIntegrationCreateDTO(
        @NotNull Long departmentId,
        @NotNull TemplateType resourceType,
        @NotNull Long resourceId,
        @NotNull Instant startDatetime,
        @NotNull Instant endDatetime,
        @NotNull Long patientId,
        Long defaultServiceId,
        Long defaultPractitionerId,
        String reason,
        EncounterReason service,
        EncounterPriority priority,
        String originType,
        String originName,
        String note,
        Long followUpEncounterId,
        String hl7AppointmentNumber

) {
}

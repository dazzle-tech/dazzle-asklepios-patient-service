package com.dazzle.asklepios.web.rest.vm.appointment;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.TemplateType;

import java.time.Instant;

public record AppointmentDetailsVM(
        Long id,
        Long facilityId,
        Long departmentId,
        Long availabilityGenerationBatchId,
        TemplateType resourceType,
        Long resourceId,
        String resourceName,
        Integer capacityIndex,
        Instant startDatetime,
        Instant endDatetime,
        Long patientId,
        Long defaultServiceId,
        Long defaultPractitionerId,
        Boolean requirePractitioner,
        String reason,
        EncounterReason service,
        Long serviceGroupId,
        BookingMode bookingMode,
        AppointmentStatus status,
        Boolean deferred,
        Instant deferredAt,
        Boolean requireConfirmation,
        String noShowReason,
        String cancelReason,
        String cancelledBy,
        EncounterPriority priority,
        String originType,
        String originName,
        String note,
        Long followUpEncounterId,
        Instant confirmedAt,
        Instant checkedInAt,
        Long bookingGroupId,
        Long waitingListId,
        String hl7AppointmentNumber
) {
}

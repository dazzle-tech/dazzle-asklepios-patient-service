package com.dazzle.asklepios.web.rest.vm.appointment;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.TemplateType;

import java.time.Instant;

public record AppointmentTransferVM(
        Long id,
        Long patientId,
        String patientName,
        String medicalRecordNumber,

        Long departmentId,
        String departmentName,

        Long practitionerId,
        String practitionerName,

        TemplateType resourceType,
        Long resourceId,

        Instant startDatetime,
        Instant endDatetime,

        AppointmentStatus status,
        BookingMode bookingMode
) {
}

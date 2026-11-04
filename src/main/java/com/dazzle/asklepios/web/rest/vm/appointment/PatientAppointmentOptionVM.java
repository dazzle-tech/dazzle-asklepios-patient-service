package com.dazzle.asklepios.web.rest.vm.appointment;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;

import java.time.Instant;

public record PatientAppointmentOptionVM(
        Long id,
        Instant startDatetime,
        Instant endDatetime,
        AppointmentStatus status,
        EncounterReason service
) {
    public static PatientAppointmentOptionVM from(Appointment appointment) {
        return new PatientAppointmentOptionVM(
                appointment.getId(),
                appointment.getStartDatetime(),
                appointment.getEndDatetime(),
                appointment.getStatus(),
                appointment.getService()
        );
    }
}

package com.dazzle.asklepios.web.rest.vm.appointment;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.PatientEncounter;

public record AppointmentQuickAppointmentResponseVM(
        Appointment appointment,
        PatientEncounter encounter
) {
}

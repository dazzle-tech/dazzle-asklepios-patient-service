package com.dazzle.asklepios.web.rest.vm.appointmentFromTemplate;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.PatientEncounter;

public record AppointmentFromTemplateQuickAppointmentResponseVM(
        AppointmentFromTemplate appointmentFromTemplate,
        PatientEncounter encounter
) {
}

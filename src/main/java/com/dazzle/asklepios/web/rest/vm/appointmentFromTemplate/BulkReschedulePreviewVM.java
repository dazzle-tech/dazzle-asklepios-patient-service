package com.dazzle.asklepios.web.rest.vm.appointmentFromTemplate;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;

import java.util.List;

public record BulkReschedulePreviewVM(
        List<AppointmentFromTemplate> bookedOrConfirmedAppointments
) {}

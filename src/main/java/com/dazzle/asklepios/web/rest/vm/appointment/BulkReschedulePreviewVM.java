package com.dazzle.asklepios.web.rest.vm.appointment;

import com.dazzle.asklepios.domain.Appointment;

import java.util.List;

public record BulkReschedulePreviewVM(
        List<Appointment> bookedOrConfirmedAppointments
) {}

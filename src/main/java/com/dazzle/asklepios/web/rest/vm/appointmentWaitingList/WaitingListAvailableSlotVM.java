package com.dazzle.asklepios.web.rest.vm.appointmentWaitingList;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;

import java.time.Instant;

public record WaitingListAvailableSlotVM(
        Long appointmentId,
        Instant startDatetime,
        Instant endDatetime,
        AppointmentStatus status,
        BookingMode bookingMode,
        Long practitionerId,
        Long serviceId
) {}

package com.dazzle.asklepios.web.rest.vm.appointmentRequest;

import com.dazzle.asklepios.domain.enumeration.DayOfWeek;

import java.time.Instant;
import java.time.LocalDate;

public record RecurringAppointmentDayVM(
        LocalDate date,
        DayOfWeek dayOfWeek,
        Long appointmentId,
        Instant startDatetime,
        Instant endDatetime,
        boolean hasAvailableSlot
) {
}

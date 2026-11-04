package com.dazzle.asklepios.web.rest.vm.appointmentRequest;

import com.dazzle.asklepios.domain.enumeration.DayOfWeek;

import java.time.Instant;
import java.time.LocalDate;

public record RecurringUnavailableSlotVM(
        LocalDate date,
        DayOfWeek dayOfWeek,
        Instant startDatetime,
        Instant endDatetime,
        String reason
) {
}

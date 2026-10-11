package com.dazzle.asklepios.web.rest.vm.appointmentRequest;

import com.dazzle.asklepios.domain.enumeration.DayOfWeek;

import java.time.LocalDate;

public record RecurringSkippedDayVM(
        LocalDate date,
        DayOfWeek dayOfWeek,
        String reason
) {
}

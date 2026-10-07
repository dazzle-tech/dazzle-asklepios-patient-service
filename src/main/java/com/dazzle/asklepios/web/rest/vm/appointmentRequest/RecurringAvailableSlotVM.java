package com.dazzle.asklepios.web.rest.vm.appointmentRequest;

import java.time.Instant;
import java.time.LocalDate;

public record RecurringAvailableSlotVM(
        Long appointmentId,
        LocalDate date,
        Instant startDatetime,
        Instant endDatetime
) {
}

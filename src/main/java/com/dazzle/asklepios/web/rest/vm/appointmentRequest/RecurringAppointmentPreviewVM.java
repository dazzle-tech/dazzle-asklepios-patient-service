package com.dazzle.asklepios.web.rest.vm.appointmentRequest;

import java.util.List;

public record RecurringAppointmentPreviewVM(
        List<RecurringAppointmentDayVM> days,
        List<RecurringSkippedDayVM> skippedDays,
        List<RecurringAvailableSlotVM> availableSlots,
        List<RecurringUnavailableSlotVM> unavailableSlots
) {
}

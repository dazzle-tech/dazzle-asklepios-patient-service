package com.dazzle.asklepios.web.rest.vm.appointmentRequest;

import java.util.List;

public record RecurringAppointmentRequestCreateResponseVM(
        List<AppointmentRequestResponseVM> created,
        List<RecurringSkippedDayVM> skippedDays,
        int daysWithoutSlot
) {
}

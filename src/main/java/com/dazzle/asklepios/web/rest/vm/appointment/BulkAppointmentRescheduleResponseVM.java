package com.dazzle.asklepios.web.rest.vm.appointment;

import java.util.List;

public record BulkAppointmentRescheduleResponseVM(
        boolean success,
        String message,
        List<Long> unmatchedOldAppointmentIds
) {
}

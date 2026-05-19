package com.dazzle.asklepios.web.rest.vm.appointmentFromTemplate;

import java.util.List;

public record BulkAppointmentRescheduleResponseVM(
        boolean success,
        String message,
        List<Long> unmatchedOldAppointmentIds
) {
}

package com.dazzle.asklepios.web.rest.vm.appointment;

import java.util.List;

public record BulkAppointmentTransferResponseVM(

        boolean success,

        String message,

        int transferredCount,

        List<Long> oldAppointmentIds,

        List<Long> newAppointmentIds

) {
}
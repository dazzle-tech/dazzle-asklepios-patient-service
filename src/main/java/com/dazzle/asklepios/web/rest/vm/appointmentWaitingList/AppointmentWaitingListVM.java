package com.dazzle.asklepios.web.rest.vm.appointmentWaitingList;

import com.dazzle.asklepios.domain.enumeration.WaitingListPriority;
import com.dazzle.asklepios.domain.enumeration.WaitingListStatus;

import java.time.Instant;
import java.time.LocalDate;

public record AppointmentWaitingListVM(
        Long id,
        Long patientId,
        String patientName,
        String patientMrn,
        Long departmentId,
        Long serviceId,
        Long practitionerId,
        WaitingListPriority priority,
        WaitingListStatus status,
        LocalDate preferredDate,
        Integer expectedDurationMinutes,
        String reason,
        String notes,
        Long bookingGroupId,
        Instant bookedAt,
       Instant createdDate
) {
}

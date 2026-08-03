package com.dazzle.asklepios.service.dto.appointmentWaitingList;

import com.dazzle.asklepios.domain.enumeration.WaitingListPriority;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AppointmentWaitingListCreateDTO(
        @NotNull Long patientId,
        @NotNull Long facilityId,
        @NotNull Long departmentId,
        Long serviceId,
        Long practitionerId,
        WaitingListPriority priority,
        LocalDate preferredDate,
        String reason,
        String notes,
       @NotNull Integer expectedDurationMinutes
) {
}

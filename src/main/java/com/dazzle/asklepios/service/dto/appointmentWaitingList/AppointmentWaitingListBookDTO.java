package com.dazzle.asklepios.service.dto.appointmentWaitingList;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AppointmentWaitingListBookDTO(
        @NotEmpty List<Long> appointmentIds,
        String notes
) {
}

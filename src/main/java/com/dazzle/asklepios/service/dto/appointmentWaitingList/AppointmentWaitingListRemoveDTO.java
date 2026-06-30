package com.dazzle.asklepios.service.dto.appointmentWaitingList;

import jakarta.validation.constraints.NotNull;

public record AppointmentWaitingListRemoveDTO(
        @NotNull String reason
) {
}
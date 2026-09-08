package com.dazzle.asklepios.service.dto.appointment;

import jakarta.validation.constraints.NotNull;

public record AppointmentTransferMappingDTO(

        @NotNull
        Long oldAppointmentId,

        @NotNull
        Long newAppointmentId

) {
}
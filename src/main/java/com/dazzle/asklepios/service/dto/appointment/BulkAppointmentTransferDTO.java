package com.dazzle.asklepios.service.dto.appointment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkAppointmentTransferDTO(

        @NotEmpty
        List<@Valid AppointmentTransferMappingDTO> transfers

) {
}
package com.dazzle.asklepios.service.dto.appointmentRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppointmentRequestCancelDTO(

        @NotBlank
        @Size(max = 255)
        String cancelReason

){

}
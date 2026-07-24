package com.dazzle.asklepios.service.dto.appointmentPolicyAssignment;

import jakarta.validation.constraints.NotNull;

public record AppointmentPolicyAssignmentAppliedUpdateDTO(
        @NotNull Long id,
        @NotNull Boolean isApplied
) {
}

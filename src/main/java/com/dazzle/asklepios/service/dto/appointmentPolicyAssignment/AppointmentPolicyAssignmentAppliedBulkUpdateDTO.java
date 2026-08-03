package com.dazzle.asklepios.service.dto.appointmentPolicyAssignment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AppointmentPolicyAssignmentAppliedBulkUpdateDTO(
        @NotEmpty List<@Valid AppointmentPolicyAssignmentAppliedUpdateDTO> updates
) {
}
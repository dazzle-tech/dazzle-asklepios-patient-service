package com.dazzle.asklepios.web.rest.vm.appointmentPolicyAssignment;

import com.dazzle.asklepios.domain.AppointmentPolicyAssignment;

public record AppointmentPolicyAssignmentResponseVM(
        Long id,
        Long appointmentId,
        Long policyId,
        Long policyAssignmentId,
        String policyName,
        String policyCode,
        Boolean isRequired,
        Boolean isApplied
) {
}

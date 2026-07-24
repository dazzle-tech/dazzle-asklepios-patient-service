package com.dazzle.asklepios.web.rest.vm.appointmentPolicyAssignment;

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

package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.PolicyAssignmentDTO;
import com.dazzle.asklepios.domain.AppointmentPolicyAssignment;
import com.dazzle.asklepios.repository.AppointmentPolicyAssignmentRepository;
import com.dazzle.asklepios.service.helper.PolicyAssignmentHelper;
import com.dazzle.asklepios.web.rest.vm.appointmentPolicyAssignment.AppointmentPolicyAssignmentResponseVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AppointmentPolicyAssignmentService {
    private static final Logger LOG = LoggerFactory.getLogger(AppointmentPolicyAssignmentService.class);

    private final AppointmentPolicyAssignmentRepository appointmentPolicyAssignmentRepository;
    private final PolicyAssignmentHelper policyAssignmentHelper;

    @Transactional(readOnly = true)
    public List<AppointmentPolicyAssignmentResponseVM> getByAppointmentId(Long appointmentId) {
        LOG.debug("Request to get AppointmentPolicyAssignments by appointmentId: {}", appointmentId);
        List<AppointmentPolicyAssignment> assignments = appointmentPolicyAssignmentRepository.findByAppointment_Id(appointmentId);

        return assignments.stream()
                .map(assignment -> {
                    PolicyAssignmentDTO policyAssignment = policyAssignmentHelper.getPolicyAssignment(assignment.getPolicyAssignmentId());

                    String policyName = null;
                    String policyCode = null;

                    if (policyAssignment != null && policyAssignment.policy() != null) {
                        policyName = policyAssignment.policy().name();
                        policyCode = policyAssignment.policy().code();
                    }

                    return new AppointmentPolicyAssignmentResponseVM(
                            assignment.getId(),
                            assignment.getAppointment() != null ? assignment.getAppointment().getId() : null,
                            assignment.getPolicyId(),
                            assignment.getPolicyAssignmentId(),
                            policyName,
                            policyCode,
                            assignment.getIsRequired(),
                            assignment.getIsApplied()
                    );
                })
                .toList();
    }

}

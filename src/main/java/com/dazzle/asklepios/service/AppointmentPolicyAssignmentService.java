package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.PolicyAssignmentDTO;
import com.dazzle.asklepios.domain.AppointmentPolicyAssignment;
import com.dazzle.asklepios.repository.AppointmentPolicyAssignmentRepository;
import com.dazzle.asklepios.service.dto.appointmentPolicyAssignment.AppointmentPolicyAssignmentAppliedBulkUpdateDTO;
import com.dazzle.asklepios.service.dto.appointmentPolicyAssignment.AppointmentPolicyAssignmentAppliedUpdateDTO;
import com.dazzle.asklepios.service.helper.PolicyAssignmentHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentPolicyAssignment.AppointmentPolicyAssignmentResponseVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                .map(this::toResponseVMWithPolicyDetails)
                .toList();
    }

    public List<AppointmentPolicyAssignmentResponseVM> bulkUpdateApplied(AppointmentPolicyAssignmentAppliedBulkUpdateDTO dto) {
        LOG.debug("Bulk update appointment policy assignment applied flags: {}", dto);

        if (dto == null || dto.updates() == null || dto.updates().isEmpty()) {
            throw new BadRequestAlertException(
                    "updatesrequired",
                    "AppointmentPolicyAssignment",
                    "At least one update is required"
            );
        }

        Map<Long, Boolean> updatesById = new LinkedHashMap<>();

        for (AppointmentPolicyAssignmentAppliedUpdateDTO update : dto.updates()) {
            if (update == null || update.id() == null || update.isApplied() == null) {
                throw new BadRequestAlertException(
                        "invalidupdate",
                        "AppointmentPolicyAssignment",
                        "Each update must contain id and isApplied"
                );
            }

            updatesById.put(update.id(), update.isApplied());
        }

        List<Long> ids = new ArrayList<>(updatesById.keySet());

        List<AppointmentPolicyAssignment> existingAssignments = appointmentPolicyAssignmentRepository.findAllById(ids);

        if (existingAssignments.size() != ids.size()) {
            List<Long> existingIds = existingAssignments.stream()
                    .map(AppointmentPolicyAssignment::getId)
                    .toList();

            List<Long> missingIds = ids.stream()
                    .filter(id -> !existingIds.contains(id))
                    .toList();

            throw new BadRequestAlertException(
                    "idsnotfound",
                    "AppointmentPolicyAssignment",
                    "Appointment policy assignment not found for ids: " + missingIds
            );
        }

        for (AppointmentPolicyAssignment assignment : existingAssignments) {
            assignment.setIsApplied(updatesById.get(assignment.getId()));
        }

        List<AppointmentPolicyAssignment> savedAssignments =
                appointmentPolicyAssignmentRepository.saveAll(existingAssignments);

        return savedAssignments.stream()
                .map(this::toResponseVMWithPolicyDetails)
                .toList();
    }

    private AppointmentPolicyAssignmentResponseVM toResponseVMWithPolicyDetails(AppointmentPolicyAssignment assignment) {
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
    }
}

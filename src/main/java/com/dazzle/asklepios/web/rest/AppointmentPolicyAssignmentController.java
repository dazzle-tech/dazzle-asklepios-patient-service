package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.AppointmentPolicyAssignmentService;
import com.dazzle.asklepios.service.dto.appointmentPolicyAssignment.AppointmentPolicyAssignmentAppliedBulkUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentPolicyAssignment.AppointmentPolicyAssignmentResponseVM;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class AppointmentPolicyAssignmentController {

    private final AppointmentPolicyAssignmentService appointmentPolicyAssignmentService;

    @GetMapping("/appointment-policy-assignment/by-appointment-id/{appointmentId}")
    public ResponseEntity<List<AppointmentPolicyAssignmentResponseVM>> getAppointmentPolicyAssignmentByAppointmentId(@PathVariable Long appointmentId) {
        List<AppointmentPolicyAssignmentResponseVM> result = appointmentPolicyAssignmentService.getByAppointmentId(appointmentId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointment-policy-assignment/bulk-update-applied")
    public ResponseEntity<List<AppointmentPolicyAssignmentResponseVM>> bulkUpdateApplied(@Valid @RequestBody AppointmentPolicyAssignmentAppliedBulkUpdateDTO dto) {
        if (dto == null || dto.updates() == null || dto.updates().isEmpty()) {
            throw new BadRequestAlertException(
                    "updatesrequired",
                    "AppointmentPolicyAssignment",
                    "At least one policy assignment update is required"
            );
        }
        List<AppointmentPolicyAssignmentResponseVM> result = appointmentPolicyAssignmentService.bulkUpdateApplied(dto);

        return ResponseEntity.ok(result);
    }
}

package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.AppointmentPolicyAssignmentService;
import com.dazzle.asklepios.web.rest.vm.appointmentPolicyAssignment.AppointmentPolicyAssignmentResponseVM;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}

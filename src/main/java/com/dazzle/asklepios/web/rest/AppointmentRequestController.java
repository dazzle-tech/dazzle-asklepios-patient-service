package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.enumeration.AppointmentRequestStatus;
import com.dazzle.asklepios.service.AppointmentRequestService;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestCancelDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestCreateDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.AppointmentRequestResponseVM;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class AppointmentRequestController {

    private final AppointmentRequestService appointmentRequestService;

    @PostMapping("/appointment-requests")
    public ResponseEntity<AppointmentRequestResponseVM> create(
            @Valid @RequestBody AppointmentRequestCreateDTO dto
    ) {
        AppointmentRequestResponseVM result = appointmentRequestService.create(dto);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointment-requests/approve")
    public ResponseEntity<AppointmentRequestResponseVM> approve(
            @Valid @RequestBody AppointmentRequestUpdateDTO dto
    ) {
        if (dto.id() == null) {
            throw new BadRequestAlertException(
                    "Invalid id",
                    "appointmentRequest",
                    "idnull"
            );
        }

        AppointmentRequestResponseVM result = appointmentRequestService.approve(dto);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/appointment-requests/{id}")
    public ResponseEntity<AppointmentRequestResponseVM> getById(
            @PathVariable("id") @NotNull Long id
    ) {
        AppointmentRequestResponseVM result = appointmentRequestService.getById(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/appointment-requests")
    public ResponseEntity<List<AppointmentRequestResponseVM>> getAll(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "facilityId", required = false) Long facilityId,
            @RequestParam(value = "sourceEncounterId", required = false) Long sourceEncounterId,
            @RequestParam(value = "status", required = false) AppointmentRequestStatus status
    ) {
        if (patientId != null) {
            return ResponseEntity.ok(appointmentRequestService.getByPatientId(patientId));
        }

        if (facilityId != null) {
            return ResponseEntity.ok(appointmentRequestService.getByFacilityIdAndBookableDepartment(facilityId));
        }

        if (sourceEncounterId != null) {
            return ResponseEntity.ok(appointmentRequestService.getBySourceEncounterId(sourceEncounterId));
        }

        if (status != null) {
            return ResponseEntity.ok(appointmentRequestService.getByStatus(status));
        }

        return ResponseEntity.ok(appointmentRequestService.getAll());
    }


    @PutMapping("/appointment-requests/{id}/cancel")
    public ResponseEntity<AppointmentRequestResponseVM> cancel(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentRequestCancelDTO dto
    ) {
        AppointmentRequestResponseVM result = appointmentRequestService.cancel(id, dto);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/appointment-requests/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        appointmentRequestService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
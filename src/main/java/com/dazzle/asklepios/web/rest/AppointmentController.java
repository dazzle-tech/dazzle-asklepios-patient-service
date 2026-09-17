package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.service.AppointmentService;
import com.dazzle.asklepios.service.dto.appointment.AppointmentBookPatientDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentCancelDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentIntegrationCreateDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentNoShowDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentQuickAppointmentDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentSearchFilterMultiDepartmentDTO;
import com.dazzle.asklepios.service.dto.appointment.BulkAppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.appointment.BulkAppointmentTransferDTO;
import com.dazzle.asklepios.service.dto.appointment.DiagnosticTestAppointmentRescheduleDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.appointment.AppointmentLogResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.AppointmentQuickAppointmentResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.AppointmentTransferVM;
import com.dazzle.asklepios.web.rest.vm.appointment.BulkAppointmentRescheduleResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.BulkAppointmentTransferResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.BulkReschedulePreviewVM;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private static final String ENTITY_NAME = "Appointment";

    @PutMapping("/appointments/book-patient")
    public ResponseEntity<Appointment> bookPatientAppointment(@Valid @RequestBody AppointmentBookPatientDTO dto) {
        if (dto.service() == EncounterReason.FOLLOW_UP && dto.followUpEncounterId() == null) {
            throw new BadRequestAlertException("Follow Up Encounter is require", "Appointment", "followUpEncounterId.invalid");
        }
        Appointment result = appointmentService.bookPatientAppointment(dto);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/appointments/by-status-and-dates")
    public ResponseEntity<List<Appointment>> getAppointmentsByStatusBetweenDates(@RequestParam("status") List<AppointmentStatus> status, @RequestParam("startDatetime") Instant startDatetime, @RequestParam("endDatetime") Instant endDatetime, Pageable pageable) {

        if (startDatetime == null || endDatetime == null) {
            throw new BadRequestAlertException("startDatetime and endDatetime are required", "appointment", "payload.required");
        }

        if (startDatetime.isAfter(endDatetime)) {
            throw new BadRequestAlertException("startDatetime must be before or equal to endDatetime", "appointment", "payload.required");
        }

        Page<Appointment> result = appointmentService.getAppointmentsByStatusBetweenDates(status, startDatetime, endDatetime, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                result
        );

        return new ResponseEntity<>(result.getContent(), headers, HttpStatus.OK);
    }

    @PostMapping("/appointments/search")
    public ResponseEntity<List<Appointment>> filterAppointments(@Valid @RequestBody AppointmentSearchFilterMultiDepartmentDTO filter, Pageable pageable) {
        Page<Appointment> appointment = appointmentService.filterAppointment(filter, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                appointment
        );
        return new ResponseEntity<>(appointment.getContent(), headers, HttpStatus.OK);
    }

    @PostMapping("/appointments/patient-portal/search")
    public ResponseEntity<List<Appointment>> filterAppointmentsByPatientPortal(@Valid @RequestBody AppointmentSearchFilterMultiDepartmentDTO filter, Pageable pageable) {
        Page<Appointment> appointment = appointmentService.filterAppointmentByPatientPortal(filter, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                appointment
        );
        return new ResponseEntity<>(appointment.getContent(), headers, HttpStatus.OK);
    }

    @PutMapping("/appointments/cancel")
    public ResponseEntity<Appointment> cancel(@Valid @RequestBody AppointmentCancelDTO dto) {
        Appointment result = appointmentService.cancel(dto);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/no-show")
    public ResponseEntity<Appointment> noShow(@Valid @RequestBody AppointmentNoShowDTO dto) {
        Appointment result = appointmentService.noShow(dto);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/{id}/confirm")
    public ResponseEntity<Appointment> confirm(@PathVariable Long id) {
        Appointment result = appointmentService.confirm(id);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/{id}/undo-confirm")
    public ResponseEntity<Appointment> undoConfirm(@PathVariable Long id) {
        Appointment result = appointmentService.undoConfirm(id);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/{id}/check-in")
    public ResponseEntity<Appointment> checkIn(@PathVariable Long id) {
        Appointment result = appointmentService.checkIn(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/appointments/quick-appointment")
    public ResponseEntity<AppointmentQuickAppointmentResponseVM> createQuickAppointment(@Valid @RequestBody AppointmentQuickAppointmentDTO appointmentDTO) {
        if (appointmentDTO.service() == EncounterReason.FOLLOW_UP && appointmentDTO.followUpEncounterId() == null) {
            throw new BadRequestAlertException("Follow Up Encounter is require", "Appointment", "followUpEncounterId.invalid");
        }
        AppointmentQuickAppointmentResponseVM result = appointmentService.createQuickAppointment(appointmentDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/appointments/by-batch-id/{batchId}")
    public ResponseEntity<List<Appointment>> getAppointmentsByBatchId(@PathVariable Long batchId, Pageable pageable) {
        Page<Appointment> result = appointmentService.getAppointmentByAvailabilityGenerationBatch(batchId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                result
        );

        return new ResponseEntity<>(result.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/appointments/by-department-and-dates")
    public ResponseEntity<List<Appointment>> getAppointmentsByDepartmentBetweenDates(@RequestParam("departmentId") Long departmentId, @RequestParam("startDatetime") Instant startDatetime, @RequestParam("endDatetime") Instant endDatetime, Pageable pageable) {
        if (startDatetime == null || endDatetime == null) {
            throw new BadRequestAlertException("startDatetime and endDatetime are required", "appointment", "payload.required");
        }

        if (startDatetime.isAfter(endDatetime)) {
            throw new BadRequestAlertException("startDatetime must be before or equal to endDatetime", "appointment", "payload.required");
        }
        Page<Appointment> result = appointmentService.getAppointmentsByDepartmentBetweenDates(departmentId, startDatetime, endDatetime, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                result
        );

        return new ResponseEntity<>(result.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/appointments/{appointmentId}/logs")
    public ResponseEntity<List<AppointmentLogResponseVM>> getAppointmentLogs(@PathVariable Long appointmentId) {
        List<AppointmentLogResponseVM> logs = appointmentService.getAppointmentLogs(appointmentId);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<Appointment> getAppointmentById(@PathVariable("id") @NotNull Long appointmentId) {

        Appointment appointment = appointmentService.getById(appointmentId);
        return ResponseEntity.ok(appointment);
    }

    @PostMapping("/appointments/reschedule")
    public ResponseEntity<Appointment> reschedule(@Valid @RequestBody AppointmentRescheduleDTO dto) {
        Appointment result = appointmentService.reschedule(dto);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/appointments/reschedule-diagnostic-test")
    public ResponseEntity<Appointment> rescheduleDiagnosticTestAppointment(@Valid @RequestBody DiagnosticTestAppointmentRescheduleDTO dto) {
        Appointment result = appointmentService.rescheduleDiagnosticTestAppointment(dto);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/appointments/bulk-reschedule/preview/{batchId}")
    public ResponseEntity<BulkReschedulePreviewVM> getBulkReschedulePreview(@PathVariable Long batchId, @RequestParam boolean includeFreeSlots, @RequestParam(required = false) Long departmentId, @RequestParam(required = false) String templateName) {
        BulkReschedulePreviewVM result = appointmentService.getBulkReschedulePreview(batchId, includeFreeSlots, departmentId, templateName);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/bulk-reschedule/cancel/{batchId}")
    public ResponseEntity<Void> cancelBulkRescheduleAppointments(@PathVariable Long batchId) {
        appointmentService.cancelBulkRescheduleAppointments(batchId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/appointments/bulk-reschedule")
    public ResponseEntity<BulkAppointmentRescheduleResponseVM> bulkReschedule(@Valid @RequestBody BulkAppointmentRescheduleDTO dto) {
        validateBulkRescheduleDto(dto);

        BulkAppointmentRescheduleResponseVM result = appointmentService.bulkReschedule(dto);

        if (result.success()) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    @PostMapping("/appointments/bulk-reschedule/notify-patient/{batchId}")
    public ResponseEntity<Void> notifyPatientForAppointmentReschedule(@PathVariable Long batchId) {
        appointmentService.notifyPatientForAppointmentReschedule(batchId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/appointments/by-status-and-dates/without-pagination")
    public ResponseEntity<List<Appointment>> getAppointmentsByStatusBetweenDatesWithoutPagination(
            @RequestParam("status") List<AppointmentStatus> status,
            @RequestParam("startDatetime") Instant startDatetime,
            @RequestParam("endDatetime") Instant endDatetime
    ) {

        if (startDatetime == null || endDatetime == null) {
            throw new BadRequestAlertException(
                    "startDatetime and endDatetime are required",
                    "appointment",
                    "payload.required"
            );
        }

        if (startDatetime.isAfter(endDatetime)) {
            throw new BadRequestAlertException(
                    "startDatetime must be before or equal to endDatetime",
                    "appointment",
                    "payload.required"
            );
        }

        List<Appointment> result =
                appointmentService.getAppointmentsByStatusBetweenDatesWithoutPagination(status, startDatetime, endDatetime);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/appointments/search/without-pagination")
    public ResponseEntity<List<Appointment>> filterAppointmentsWithoutPagination(@Valid @RequestBody AppointmentSearchFilterMultiDepartmentDTO filter) {
        List<Appointment> appointments = appointmentService.filterAppointmentWithoutPagination(filter);

        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/appointments/cancelled")
    public ResponseEntity<List<Appointment>> getCancelledAppointments(@RequestParam Instant startDatetime, @RequestParam Instant endDatetime, @RequestParam(required = false) Long departmentId, Pageable pageable) {
        if (startDatetime == null) {
            throw new BadRequestAlertException("Start datetime is required", ENTITY_NAME, "startdatetimerequired");
        }

        if (endDatetime == null) {
            throw new BadRequestAlertException("End datetime is required", ENTITY_NAME, "enddatetimerequired");
        }
        Page<Appointment> page = appointmentService.getCancelledAppointmentsBetweenDates(startDatetime, endDatetime, departmentId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(page.getContent());
    }

    @PostMapping("/appointments/integration")
    public ResponseEntity<Appointment> createIntegrationAppointment(@Valid @RequestBody AppointmentIntegrationCreateDTO dto) {
        Appointment appointment = appointmentService.createIntegrationAppointment(dto);
        return ResponseEntity.ok(appointment);
    }

    @GetMapping("/appointments/transfer/sources")
    public ResponseEntity<List<AppointmentTransferVM>> getTransferSourceAppointments(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(
                appointmentService.getTransferSourceAppointments(
                        startDate,
                        endDate
                )
        );
    }

    @GetMapping("/appointments/transfer/targets")
    public ResponseEntity<List<AppointmentTransferVM>> getTransferTargetAppointments(@RequestParam Long departmentId, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(
                appointmentService.getTransferTargetAppointments(
                        departmentId,
                        startDate,
                        endDate
                )
        );
    }

    @PostMapping("/appointments/bulk-transfer")
    public ResponseEntity<BulkAppointmentTransferResponseVM> bulkAppointmentTransfer(@Valid @RequestBody BulkAppointmentTransferDTO dto) {

        return ResponseEntity.ok(appointmentService.bulkAppointmentTransfer(dto));
    }

    private void validateBulkRescheduleDto(BulkAppointmentRescheduleDTO dto) {
        if (dto.originalAvailabilityGenerationBatchId() == null) {
            throw new BadRequestAlertException(
                    "Original generation batch is required",
                    "Appointment",
                    "originalbatch.required"
            );
        }

        if (dto.replacementAvailabilityGenerationBatchId() == null) {
            throw new BadRequestAlertException(
                    "Replacement generation batch is required",
                    "Appointment",
                    "replacementbatch.required"
            );
        }

        if (dto.originalAvailabilityGenerationBatchId().equals(dto.replacementAvailabilityGenerationBatchId())) {
            throw new BadRequestAlertException(
                    "Original and replacement generation batches cannot be the same",
                    "Appointment",
                    "samebatch.invalid"
            );
        }
    }

}

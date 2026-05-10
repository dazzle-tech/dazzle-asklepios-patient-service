package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.AppointmentLog;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.service.AppointmentFromTemplateService;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateBookPatientDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateCancelDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateNoShowDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateQuickAppointmentDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateRescheduleDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.AppointmentFromTemplateSearchFilterDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.BulkAppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.appointmentFromTemplate.DiagnosticTestAppointmentRescheduleDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentFromTemplate.AppointmentFromTemplateQuickAppointmentResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointmentFromTemplate.BulkAppointmentRescheduleResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointmentFromTemplate.BulkReschedulePreviewVM;
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
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class AppointmentFormTemplateController {

    private final AppointmentFromTemplateService appointmentFromTemplateService;

    @PutMapping("/appointments/book-patient")
    public ResponseEntity<AppointmentFromTemplate> bookPatientAppointment(@Valid @RequestBody AppointmentFromTemplateBookPatientDTO dto) {
        if (dto.service() == EncounterReason.FOLLOW_UP && dto.followUpEncounterId() == null) {
            throw new BadRequestAlertException("Follow Up Encounter is require", "Appointment", "followUpEncounterId.invalid");
        }
        AppointmentFromTemplate result = appointmentFromTemplateService.bookPatientAppointment(dto);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/appointments/by-status-and-dates")
    public ResponseEntity<List<AppointmentFromTemplate>> getAppointmentsByStatusBetweenDates(@RequestParam("status") List<AppointmentStatus> status, @RequestParam("startDatetime") Instant startDatetime, @RequestParam("endDatetime") Instant endDatetime, Pageable pageable) {

        if (startDatetime == null || endDatetime == null) {
            throw new BadRequestAlertException("startDatetime and endDatetime are required", "appointmentFormTemplate", "payload.required");
        }

        if (startDatetime.isAfter(endDatetime)) {
            throw new BadRequestAlertException("startDatetime must be before or equal to endDatetime", "appointmentFormTemplate", "payload.required");
        }

        Page<AppointmentFromTemplate> result = appointmentFromTemplateService.getAppointmentsByStatusBetweenDates(status, startDatetime, endDatetime, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                result
        );

        return new ResponseEntity<>(result.getContent(), headers, HttpStatus.OK);
    }

    @PostMapping("/appointments/search")
    public ResponseEntity<List<AppointmentFromTemplate>> filterAppointments(@Valid @RequestBody AppointmentFromTemplateSearchFilterDTO filter, Pageable pageable) {
        Page<AppointmentFromTemplate> appointment = appointmentFromTemplateService.filterAppointment(filter, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                appointment
        );
        return new ResponseEntity<>(appointment.getContent(), headers, HttpStatus.OK);
    }

    @PutMapping("/appointments/cancel")
    public ResponseEntity<AppointmentFromTemplate> cancel(@Valid @RequestBody AppointmentFromTemplateCancelDTO dto) {
        AppointmentFromTemplate result = appointmentFromTemplateService.cancel(dto);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/no-show")
    public ResponseEntity<AppointmentFromTemplate> noShow(@Valid @RequestBody AppointmentFromTemplateNoShowDTO dto) {
        AppointmentFromTemplate result = appointmentFromTemplateService.noShow(dto);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/{id}/confirm")
    public ResponseEntity<AppointmentFromTemplate> confirm(@PathVariable Long id) {
        AppointmentFromTemplate result = appointmentFromTemplateService.confirm(id);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/{id}/check-in")
    public ResponseEntity<AppointmentFromTemplate> checkIn(@PathVariable Long id) {
        AppointmentFromTemplate result = appointmentFromTemplateService.checkIn(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/appointments/quick-appointment")
    public ResponseEntity<AppointmentFromTemplateQuickAppointmentResponseVM> createQuickAppointment(@Valid @RequestBody AppointmentFromTemplateQuickAppointmentDTO appointmentDTO) {
        if (appointmentDTO.service() == EncounterReason.FOLLOW_UP && appointmentDTO.followUpEncounterId() == null) {
            throw new BadRequestAlertException("Follow Up Encounter is require", "Appointment", "followUpEncounterId.invalid");
        }
        AppointmentFromTemplateQuickAppointmentResponseVM result = appointmentFromTemplateService.createQuickAppointment(appointmentDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/appointments/by-batch-id/{batchId}")
    public ResponseEntity<List<AppointmentFromTemplate>> getAppointmentsByBatchId(@PathVariable Long batchId, Pageable pageable) {
        Page<AppointmentFromTemplate> result = appointmentFromTemplateService.getAppointmentByAvailabilityGenerationBatch(batchId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                result
        );

        return new ResponseEntity<>(result.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/appointments/by-department-and-dates")
    public ResponseEntity<List<AppointmentFromTemplate>> getAppointmentsByDepartmentBetweenDates(@RequestParam("departmentId") Long departmentId, @RequestParam("startDatetime") Instant startDatetime, @RequestParam("endDatetime") Instant endDatetime, Pageable pageable) {
        if (startDatetime == null || endDatetime == null) {
            throw new BadRequestAlertException("startDatetime and endDatetime are required", "appointmentFormTemplate", "payload.required");
        }

        if (startDatetime.isAfter(endDatetime)) {
            throw new BadRequestAlertException("startDatetime must be before or equal to endDatetime", "appointmentFormTemplate", "payload.required");
        }
        Page<AppointmentFromTemplate> result = appointmentFromTemplateService.getAppointmentsByDepartmentBetweenDates(departmentId, startDatetime, endDatetime, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                result
        );

        return new ResponseEntity<>(result.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/appointments/{appointmentId}/logs")
    public ResponseEntity<List<AppointmentLog>> getAppointmentLogs(@PathVariable Long appointmentId) {
        List<AppointmentLog> logs = appointmentFromTemplateService.getAppointmentLogs(appointmentId);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<AppointmentFromTemplate> getAppointmentById(@PathVariable("id") @NotNull Long appointmentId) {

        AppointmentFromTemplate appointment = appointmentFromTemplateService.getById(appointmentId);
        return ResponseEntity.ok(appointment);
    }

    @PostMapping("/appointments/reschedule")
    public ResponseEntity<AppointmentFromTemplate> reschedule(@Valid @RequestBody AppointmentFromTemplateRescheduleDTO dto) {
        AppointmentFromTemplate result = appointmentFromTemplateService.reschedule(dto);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/appointments/reschedule-diagnostic-test")
    public ResponseEntity<AppointmentFromTemplate> rescheduleDiagnosticTestAppointment(@Valid @RequestBody DiagnosticTestAppointmentRescheduleDTO dto) {
        AppointmentFromTemplate result = appointmentFromTemplateService.rescheduleDiagnosticTestAppointment(dto);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/appointments/bulk-reschedule/preview/{batchId}")
    public ResponseEntity<BulkReschedulePreviewVM> getBulkReschedulePreview(@PathVariable Long batchId) {
        BulkReschedulePreviewVM result = appointmentFromTemplateService.getBulkReschedulePreview(batchId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/appointments/bulk-reschedule/cancel/{batchId}")
    public ResponseEntity<Void> cancelBulkRescheduleAppointments(@PathVariable Long batchId) {
        appointmentFromTemplateService.cancelBulkRescheduleAppointments(batchId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/appointments/bulk-reschedule")
    public ResponseEntity<BulkAppointmentRescheduleResponseVM> bulkReschedule(
            @Valid @RequestBody BulkAppointmentRescheduleDTO dto
    ) {
        validateBulkRescheduleDto(dto);

        BulkAppointmentRescheduleResponseVM result = appointmentFromTemplateService.bulkReschedule(dto);

        if (result.success()) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
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

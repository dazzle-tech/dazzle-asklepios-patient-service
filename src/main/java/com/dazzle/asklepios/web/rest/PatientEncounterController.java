package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.service.DiagnosticOrderService;
import com.dazzle.asklepios.service.PatientEncounterService;
import com.dazzle.asklepios.service.PatientPrescriptionService;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCreateDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterDischargeDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterSearchFilterDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.PatientEncounterVM;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
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

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/patient")
public class PatientEncounterController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientEncounterController.class);

    private final PatientEncounterService patientEncounterService;
    private final DiagnosticOrderService diagnosticOrderService;
    private final PatientPrescriptionService patientPrescriptionService;
    public PatientEncounterController(PatientEncounterService patientEncounterService, DiagnosticOrderService diagnosticOrderService, PatientPrescriptionService patientPrescriptionService) {
        this.patientEncounterService = patientEncounterService;
        this.diagnosticOrderService = diagnosticOrderService;
        this.patientPrescriptionService = patientPrescriptionService;
    }

    @PostMapping("/encounter")
    public ResponseEntity<PatientEncounter> create(
            @Valid @RequestBody @NotNull PatientEncounterCreateDTO patientEncounterCreateDTO
    ) {
        LOG.debug("REST create PatientEncounter payload={}", patientEncounterCreateDTO);

        if (patientEncounterCreateDTO.patientId() == null) {
            LOG.warn("[CREATE] PatientEncounter rejected: patientId is null payload={}", patientEncounterCreateDTO);
            throw new BadRequestAlertException("Patient id is required", "patientEncounter", "patient.required");
        }
        if (patientEncounterCreateDTO.facilityId() == null) {
            LOG.warn("[CREATE] PatientEncounter rejected: facilityId is null payload={}", patientEncounterCreateDTO);
            throw new BadRequestAlertException("Facility id is required", "patientEncounter", "facility.required");
        }
        if (patientEncounterCreateDTO.departmentId() == null) {
            LOG.warn("[CREATE] PatientEncounter rejected: departmentId is null payload={}", patientEncounterCreateDTO);
            throw new BadRequestAlertException("Department id is required", "patientEncounter", "department.required");
        }

        if (patientEncounterCreateDTO.encounterReason() == EncounterReason.FOLLOW_UP && patientEncounterCreateDTO.followUpEncounterId() == null) {
            throw new BadRequestAlertException(
                    "Follow-up encounter is required when reason is FOLLOW_UP.",
                    "patientEncounter",
                    "followUpEncounter.required.followup"
            );
        }

        PatientEncounter createdPatientEncounter = patientEncounterService.create(patientEncounterCreateDTO);

        return ResponseEntity
                .created(URI.create("/api/patient/encounter/" + createdPatientEncounter.getId()))
                .body(createdPatientEncounter);
    }

    @PutMapping("/encounter/{id}")
    public ResponseEntity<PatientEncounter> update(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody @NotNull PatientEncounterUpdateDTO patientEncounterUpdateDTO
    ) {
        LOG.debug("REST update PatientEncounter id={} payload={}", id, patientEncounterUpdateDTO);

        if (patientEncounterUpdateDTO.patientId() == null) {
            LOG.warn("[UPDATE] PatientEncounter rejected: patientId is null payload={}", patientEncounterUpdateDTO);
            throw new BadRequestAlertException("Patient id is required", "patientEncounter", "patient.required");
        }
        if (patientEncounterUpdateDTO.facilityId() == null) {
            LOG.warn("[UPDATE] PatientEncounter rejected: facilityId is null payload={}", patientEncounterUpdateDTO);
            throw new BadRequestAlertException("Facility id is required", "patientEncounter", "facility.required");
        }
        if (patientEncounterUpdateDTO.departmentId() == null) {
            LOG.warn("[UPDATE] PatientEncounter rejected: departmentId is null payload={}", patientEncounterUpdateDTO);
            throw new BadRequestAlertException("Department id is required", "patientEncounter", "department.required");
        }
        if (patientEncounterUpdateDTO.encounterReason() == EncounterReason.FOLLOW_UP && patientEncounterUpdateDTO.followUpEncounterId() == null) {
            throw new BadRequestAlertException(
                    "Follow-up encounter is required when reason is FOLLOW_UP.",
                    "patientEncounter",
                    "followUpEncounter.required.followup"
            );
        }

        PatientEncounter updatedPatientEncounter = patientEncounterService.update(id, patientEncounterUpdateDTO);
        return ResponseEntity.ok(updatedPatientEncounter);
    }

    @GetMapping("/encounter/{id}/previous-encounter-completed")
    public ResponseEntity<PatientEncounter> getPreviousClosedEncounter(
            @PathVariable("id") @NotNull Long encounterId
    ) {
        LOG.debug("REST get previous CLOSED PatientEncounter for encounterId={}", encounterId);

        return patientEncounterService.getPreviousClosedEncounter(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().build());
    }

    @GetMapping("/encounter/facility/{facilityId}/count/today")
    public ResponseEntity<Long> countTodayEncountersByFacility(
            @PathVariable @NotNull Long facilityId
    ) {
        LOG.debug("REST count TODAY PatientEncounters facilityId={}", facilityId);

        long totalEncounters = patientEncounterService.countTodayEncountersByFacility(facilityId);

        return ResponseEntity.ok(totalEncounters);
    }

    @GetMapping("/encounter")
    public ResponseEntity<List<PatientEncounterVM>> filterEncounters(
            @Valid @ParameterObject PatientEncounterSearchFilterDTO filter,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST filter PatientEncounters filter={} pageable={}", filter, pageable);

        if (filter == null || filter.departmentId() == null) {
            LOG.warn("[FILTER] PatientEncounter rejected: departmentId is required filter={} pageable={}", filter, pageable);
            throw new BadRequestAlertException("Department id is required", "patientEncounter", "department.required");
        }

        Page<PatientEncounter> page = patientEncounterService.filterEncounters(filter, pageable);
        List<Long> encounterIds = page.getContent().stream()
                .map(PatientEncounter::getId)
                .toList();
        Set<Long> orderEncounterIds = diagnosticOrderService.findEncounterIdsWithOrders(encounterIds);
        Set<Long> prescriptionEncounterIds = patientPrescriptionService.findEncounterIdsWithOrders(encounterIds);
        Set<Long> observasionEncounterIds=patientEncounterService.findEncounterIdsWithObservation(encounterIds);
        List<PatientEncounterVM> vmList = page.getContent().stream()
                .map(encounter -> PatientEncounterVM.ofEntity(
                        encounter,
                        orderEncounterIds.contains(encounter.getId()),
                        prescriptionEncounterIds.contains(encounter.getId()),
                        observasionEncounterIds.contains(encounter.getId())
                ))
                .toList();

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(vmList, headers, HttpStatus.OK);
    }

    @GetMapping("/encounter/department/{departmentId}/count/today/total-patients")
    public ResponseEntity<Long> countTodayDepartmentTotalPatients(
            @PathVariable @NotNull Long departmentId
    ) {
        LOG.debug("REST count TODAY TOTAL_PATIENTS departmentId={}", departmentId);

        long total = patientEncounterService.countTodayDepartmentTotalPatients(departmentId);

        return ResponseEntity.ok(total);
    }

    @GetMapping("/encounter/department/{departmentId}/count/today/active")
    public ResponseEntity<Long> countTodayDepartmentActiveCases(
            @PathVariable @NotNull Long departmentId
    ) {
        LOG.debug("REST count TODAY ACTIVE_CASES departmentId={}", departmentId);

        long active = patientEncounterService.countTodayDepartmentActiveCases(departmentId);

        return ResponseEntity.ok(active);
    }

    @GetMapping("/encounter/department/{departmentId}/count/today/completed")
    public ResponseEntity<Long> countTodayDepartmentCompleted(
            @PathVariable @NotNull Long departmentId
    ) {
        LOG.debug("REST count TODAY COMPLETED departmentId={}", departmentId);

        long completed = patientEncounterService.countTodayDepartmentCompleted(departmentId);

        return ResponseEntity.ok(completed);
    }

    @GetMapping("/encounter/department/{departmentId}/count/today/cancelled")
    public ResponseEntity<Long> countTodayDepartmentCancelled(
            @PathVariable @NotNull Long departmentId
    ) {
        LOG.debug("REST count TODAY CANCELLED departmentId={}", departmentId);

        long cancelled = patientEncounterService.countTodayDepartmentCancelled(departmentId);

        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/encounter/patient/{patientId}/department/{departmentId}/previous")
    public ResponseEntity<List<PatientEncounter>> listPreviousEncountersSameDepartment(
            @PathVariable @NotNull Long patientId,
            @PathVariable @NotNull Long departmentId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list previous PatientEncounters patientId={} departmentId={} pageable={}",
                patientId, departmentId, pageable);

        Page<PatientEncounter> page =
                patientEncounterService.findPreviousByPatientAndDepartment(patientId, departmentId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @PostMapping("/encounter/{id}/start")
    public ResponseEntity<PatientEncounter> startEncounter(
            @PathVariable("id") @NotNull Long encounterId
    ) {
        LOG.debug("REST start PatientEncounter id={}", encounterId);
        PatientEncounter started = patientEncounterService.startEncounter(encounterId);
        return ResponseEntity.ok(started);
    }

    @PostMapping("/encounter/{id}/cancel")
    public ResponseEntity<PatientEncounter> cancelEncounter(
            @PathVariable("id") @NotNull Long encounterId
    ) {
        LOG.debug("REST cancel PatientEncounter id={}", encounterId);

        PatientEncounter cancelled = patientEncounterService.cancelEncounter(encounterId);
        return ResponseEntity.ok(cancelled);
    }

    @PostMapping("/encounter/{id}/discharge")
    public ResponseEntity<PatientEncounter> dischargeEncounter(
            @PathVariable("id") @NotNull Long encounterId,
            @Valid @RequestBody @NotNull PatientEncounterDischargeDTO dischargeDTO
    ) {
        LOG.debug("REST discharge PatientEncounter id={} payload={}", encounterId, dischargeDTO);
        // Validation: consistency between path and body
        if (!encounterId.equals(dischargeDTO.encounterId())) {
            LOG.warn("[DISCHARGE] mismatch between path id={} and body id={}", encounterId, dischargeDTO.encounterId());
            throw new BadRequestAlertException(
                    "Encounter id mismatch between path and body.",
                    "patientEncounter",
                    "discharge.id.mismatch"
            );
        }

        // Optional extra validation (before service)
        if (dischargeDTO.dischargeType() == null) {
            throw new BadRequestAlertException(
                    "Discharge type is required.",
                    "patientEncounter",
                    "discharge.type.required"
            );
        }

        if (dischargeDTO.dischargeAt() == null) {
            throw new BadRequestAlertException(
                    "Discharge date and time are required.",
                    "patientEncounter",
                    "discharge.at.required"
            );
        }

        PatientEncounter discharged =
                patientEncounterService.dischargeEncounter(dischargeDTO);

        return ResponseEntity.ok(discharged);
    }

    @PostMapping("/encounter/{id}/complete")
    public ResponseEntity<PatientEncounter> completeEncounter(
            @PathVariable("id") @NotNull Long encounterId
    ) {
        LOG.debug("REST complete PatientEncounter id={}", encounterId);

        PatientEncounter existing = patientEncounterService.completeEncounter(encounterId);
        return ResponseEntity.ok(existing);
    }

    @GetMapping("/encounter/patient/{patientId}")
    public ResponseEntity<List<PatientEncounter>> getEncountersByPatient(
            @PathVariable @NotNull Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST get PatientEncounters by patientId={} pageable={}", patientId, pageable);

        Page<PatientEncounter> page =
                patientEncounterService.getEncountersByPatientId(patientId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/encounter/{id}")
    public ResponseEntity<PatientEncounter> getEncounterById(
            @PathVariable("id") @NotNull Long encounterId
    ) {
        LOG.debug("REST get PatientEncounter by id={}", encounterId);

        PatientEncounter encounter = patientEncounterService.getById(encounterId);
        return ResponseEntity.ok(encounter);
    }

    @GetMapping("/encounter/appointment/{appointmentId}")
    public ResponseEntity<PatientEncounter> getEncountersByAppointment(
            @PathVariable @NotNull Long appointmentId
    ) {
        LOG.debug("REST get EncounterAppointment by appointmentId={}", appointmentId);

        PatientEncounter encounterAppointment = patientEncounterService.getEncountersByAppointmentId(appointmentId);
        return ResponseEntity.ok(encounterAppointment);
    }

    @PostMapping("/encounter/{id}/move-to-new")
    public ResponseEntity<PatientEncounter> moveToNew(
            @PathVariable("id") @NotNull Long encounterId
    ) {
        LOG.debug("REST move PatientEncounter from WAITING_LIST to NEW id={}", encounterId);

        PatientEncounter updated =
                patientEncounterService.moveToNew(encounterId);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/encounter/department/{departmentId}/count/date-range/waiting-list")
    public ResponseEntity<Long> countDepartmentWaitingList(
            @PathVariable @NotNull Long departmentId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        LOG.debug("REST count WAITING_LIST departmentId={} fromDate={} toDate={}",
                departmentId, fromDate, toDate);

        long total = patientEncounterService.countDepartmentWaitingListPatients(
                departmentId,
                fromDate,
                toDate
        );

        return ResponseEntity.ok(total);
    }

    @GetMapping("/encounter/department/{departmentId}/count/date-range/triage")
    public ResponseEntity<Long> countDepartmentTriage(
            @PathVariable @NotNull Long departmentId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        LOG.debug("REST count TRIAGE departmentId={} fromDate={} toDate={}",
                departmentId, fromDate, toDate);

        long total = patientEncounterService.countDepartmentInTriagePatients(
                departmentId,
                fromDate,
                toDate
        );

        return ResponseEntity.ok(total);
    }

    @GetMapping("/encounter/department/{departmentId}/count/date-range/discharged")
    public ResponseEntity<Long> countDepartmentDischarged(
            @PathVariable @NotNull Long departmentId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        LOG.debug("REST count DISCHARGED departmentId={} fromDate={} toDate={}",
                departmentId, fromDate, toDate);

        long total = patientEncounterService.countDepartmentDischargedPatients(
                departmentId,
                fromDate,
                toDate
        );

        return ResponseEntity.ok(total);
    }

    @GetMapping("/encounter/department/{departmentId}/count/date-range/total")
    public ResponseEntity<Long> countDepartmentTotalByDateRange(
            @PathVariable @NotNull Long departmentId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        LOG.debug("REST count TOTAL departmentId={} fromDate={} toDate={}",
                departmentId, fromDate, toDate);

        long total = patientEncounterService.countDepartmentEncountersByDateRange(
                departmentId,
                fromDate,
                toDate
        );

        return ResponseEntity.ok(total);
    }
    @PostMapping("/encounter/by-ids")
    public ResponseEntity<List<PatientEncounter>> getEncountersByIds(
            @RequestBody(required = false) List<Long> encounterIds
    ) {
        LOG.debug("REST get PatientEncounters by ids={}", encounterIds);

        if (encounterIds == null || encounterIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<PatientEncounter> encounters =
                patientEncounterService.getEncountersByIds(encounterIds);

        return ResponseEntity.ok(encounters);
    }
}

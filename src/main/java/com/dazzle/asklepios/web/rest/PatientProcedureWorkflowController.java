package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientProcedureMedication;
import com.dazzle.asklepios.domain.PatientProcedurePerformance;
import com.dazzle.asklepios.domain.PatientProcedurePostCare;
import com.dazzle.asklepios.domain.PatientProcedurePreAssessment;
import com.dazzle.asklepios.domain.PatientProcedureServiceEquipment;
import com.dazzle.asklepios.domain.PatientProcedureStaff;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.service.PatientProcedureWorkflowService;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureMedicationDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePerformanceDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePostCareDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePreAssessmentDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureRegistrationDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureServiceEquipmentDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureStaffDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureStatusDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/patient/procedure")
public class PatientProcedureWorkflowController {

    private final PatientProcedureWorkflowService service;

    public PatientProcedureWorkflowController(PatientProcedureWorkflowService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public ResponseEntity<List<PatientProcedure>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(required = false) ProcStatus status,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long procedureId,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @ParameterObject Pageable pageable
    ) {
        Page<PatientProcedure> page = service.search(
                fromDate, toDate, status, patientId, procedureId, includeCancelled, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<PatientProcedure> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureStatusDTO dto
    ) {
        return ResponseEntity.ok(service.changeStatus(id, dto.status()));
    }

    @PutMapping("/{id}/registration")
    public ResponseEntity<PatientProcedure> saveRegistration(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureRegistrationDTO dto
    ) {
        return ResponseEntity.ok(service.saveRegistration(id, dto));
    }

    @GetMapping("/{id}/staff")
    public ResponseEntity<List<PatientProcedureStaff>> findStaff(@PathVariable Long id) {
        return ResponseEntity.ok(service.findStaff(id));
    }

    @PostMapping("/{id}/staff")
    public ResponseEntity<PatientProcedureStaff> addStaff(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureStaffDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addStaff(id, dto));
    }

    @PutMapping("/{id}/staff/{staffId}")
    public ResponseEntity<PatientProcedureStaff> updateStaff(
            @PathVariable Long id,
            @PathVariable Long staffId,
            @Valid @RequestBody PatientProcedureStaffDTO dto
    ) {
        return ResponseEntity.ok(service.updateStaff(id, staffId, dto));
    }

    @DeleteMapping("/{id}/staff/{staffId}")
    public ResponseEntity<Void> deleteStaff(@PathVariable Long id, @PathVariable Long staffId) {
        service.deleteStaff(id, staffId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pre-assessment")
    public ResponseEntity<PatientProcedurePreAssessment> findPreAssessment(@PathVariable Long id) {
        return service.findPreAssessment(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{id}/pre-assessment")
    public ResponseEntity<PatientProcedurePreAssessment> savePreAssessment(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedurePreAssessmentDTO dto
    ) {
        return ResponseEntity.ok(service.savePreAssessment(id, dto));
    }

    @GetMapping("/{id}/performance")
    public ResponseEntity<PatientProcedurePerformance> findPerformance(@PathVariable Long id) {
        return service.findPerformance(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{id}/performance")
    public ResponseEntity<PatientProcedurePerformance> savePerformance(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedurePerformanceDTO dto
    ) {
        return ResponseEntity.ok(service.savePerformance(id, dto));
    }

    @GetMapping("/{id}/post-care")
    public ResponseEntity<PatientProcedurePostCare> findPostCare(@PathVariable Long id) {
        return service.findPostCare(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{id}/post-care")
    public ResponseEntity<PatientProcedurePostCare> savePostCare(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedurePostCareDTO dto
    ) {
        return ResponseEntity.ok(service.savePostCare(id, dto));
    }

    @GetMapping("/{id}/medications")
    public ResponseEntity<List<PatientProcedureMedication>> findMedications(@PathVariable Long id) {
        return ResponseEntity.ok(service.findMedications(id));
    }

    @PostMapping("/{id}/medications")
    public ResponseEntity<PatientProcedureMedication> addMedication(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureMedicationDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMedication(id, dto));
    }

    @PutMapping("/{id}/medications/{medicationId}")
    public ResponseEntity<PatientProcedureMedication> updateMedication(
            @PathVariable Long id,
            @PathVariable Long medicationId,
            @Valid @RequestBody PatientProcedureMedicationDTO dto
    ) {
        return ResponseEntity.ok(service.updateMedication(id, medicationId, dto));
    }

    @DeleteMapping("/{id}/medications/{medicationId}")
    public ResponseEntity<Void> deleteMedication(@PathVariable Long id, @PathVariable Long medicationId) {
        service.deleteMedication(id, medicationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/service-equipment")
    public ResponseEntity<List<PatientProcedureServiceEquipment>> findEquipment(@PathVariable Long id) {
        return ResponseEntity.ok(service.findEquipment(id));
    }

    @PostMapping("/{id}/service-equipment")
    public ResponseEntity<PatientProcedureServiceEquipment> addEquipment(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureServiceEquipmentDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addEquipment(id, dto));
    }

    @DeleteMapping("/{id}/service-equipment/{equipmentId}")
    public ResponseEntity<Void> deleteEquipment(@PathVariable Long id, @PathVariable Long equipmentId) {
        service.deleteEquipment(id, equipmentId);
        return ResponseEntity.noContent().build();
    }
}

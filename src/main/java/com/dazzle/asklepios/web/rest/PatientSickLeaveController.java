package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientSickLeave;
import com.dazzle.asklepios.service.PatientSickLeaveService;
import com.dazzle.asklepios.service.dto.patientSickLeave.PatientSickLeaveCreateDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientSickLeaveController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientSickLeaveController.class);

    private final PatientSickLeaveService patientSickLeaveService;

    public PatientSickLeaveController(PatientSickLeaveService patientSickLeaveService) {
        this.patientSickLeaveService = patientSickLeaveService;
    }

    @PostMapping("/sick-leaves")
    public ResponseEntity<PatientSickLeave> create(@Valid @RequestBody PatientSickLeaveCreateDTO dto) {
        LOG.debug("REST create PatientSickLeave payload={}", dto);
        return ResponseEntity.ok(patientSickLeaveService.create(dto));
    }

    @GetMapping("/sick-leaves/patient/{patientId}")
    public ResponseEntity<List<PatientSickLeave>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LOG.debug("REST get PatientSickLeave by patientId={} startDate={} endDate={}", patientId, startDate, endDate);
        return ResponseEntity.ok(patientSickLeaveService.getByPatientId(patientId, startDate, endDate));
    }

    @GetMapping("/sick-leaves/encounter/{encounterId}")
    public ResponseEntity<List<PatientSickLeave>> getByEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get PatientSickLeave by encounterId={}", encounterId);
        return ResponseEntity.ok(patientSickLeaveService.getByEncounterId(encounterId));
    }
}

package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import com.dazzle.asklepios.service.PatientPrescriptionService;
import com.dazzle.asklepios.service.dto.patientPrescription.PatientPrescriptionCreateDto;
import com.dazzle.asklepios.service.dto.patientPrescription.PatientPrescriptionUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
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

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientPrescriptionController {

    private final PatientPrescriptionService service;


    @PostMapping("/patient-prescriptions/create-or-get")
    public ResponseEntity<PatientPrescription> createOrGetByEncounter(
            @RequestBody PatientPrescriptionCreateDto patientPrescriptionCreateDto
    ) {
        return ResponseEntity.ok(service.createOrGetByEncounter(patientPrescriptionCreateDto));
    }

    @GetMapping("/patient-prescriptions")
    public ResponseEntity<List<PatientPrescription>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long encounterId,
            @RequestParam(required = false) PrescriptionStatus status,
            @RequestParam(required = false) PrescriptionUrgencyLevel urgencyLevel,
            @RequestParam(required = false) Long prescriptionNum,
            @RequestParam(required = false, defaultValue = "false") boolean includeCanceled,
            Pageable pageable
    ) {
        Page<PatientPrescription> page =
                service.list(patientId, encounterId, status, urgencyLevel, prescriptionNum, includeCanceled, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }


    @GetMapping("/patient-prescriptions/{id}")
    public ResponseEntity<PatientPrescription> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPrescription(id));
    }

    @PostMapping("/patient-prescriptions")
    public ResponseEntity<PatientPrescription> create(@RequestBody PatientPrescriptionCreateDto patientPrescriptionCreateDto) {
        return ResponseEntity.ok(service.create(patientPrescriptionCreateDto));
    }

    @PutMapping("/patient-prescriptions/{id}")
    public ResponseEntity<PatientPrescription> update(@PathVariable Long id, @RequestBody PatientPrescriptionUpdateDTO patientPrescriptionUpdateDTO) {
        return ResponseEntity.ok(service.update(id, patientPrescriptionUpdateDTO));
    }

    @PostMapping("/patient-prescriptions/{id}/submit")
    public ResponseEntity<PatientPrescription> submit(
            @PathVariable Long id,
            @RequestParam String lastModifiedBy
    ) {
        return ResponseEntity.ok(service.submit(id, lastModifiedBy));
    }

    @PostMapping("/patient-prescriptions/{id}/cancel")
    public ResponseEntity<PatientPrescription> cancel(
            @PathVariable Long id,
            @RequestParam String lastModifiedBy
    ) {
        return ResponseEntity.ok(service.cancel(id, lastModifiedBy));
    }
}

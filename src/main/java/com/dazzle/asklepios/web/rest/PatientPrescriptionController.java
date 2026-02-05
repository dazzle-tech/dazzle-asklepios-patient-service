package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import com.dazzle.asklepios.service.PatientPrescriptionService;
import com.dazzle.asklepios.service.vm.PatientPrescriptionCreateVM;
import com.dazzle.asklepios.service.vm.PatientPrescriptionUpdateVM;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.dto.PatientPrescriptionDTO;
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

    @GetMapping
    public ResponseEntity<List<PatientPrescriptionDTO>> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long encounterId,
            @RequestParam(required = false) PrescriptionStatus status,
            @RequestParam(required = false) PrescriptionUrgencyLevel urgencyLevel,
            @RequestParam(required = false) Long prescriptionNum,
            Pageable pageable
    ) {
        Page<PatientPrescriptionDTO> page =
                service.list(patientId, encounterId, status, urgencyLevel, prescriptionNum, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/patient-prescriptions/{id}")
    public ResponseEntity<PatientPrescriptionDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping("/patient-prescriptions")
    public ResponseEntity<PatientPrescriptionDTO> create(@RequestBody PatientPrescriptionCreateVM vm) {
        return ResponseEntity.ok(service.create(vm));
    }

    @PutMapping("/patient-prescriptions/{id}")
    public ResponseEntity<PatientPrescriptionDTO> update(@PathVariable Long id, @RequestBody PatientPrescriptionUpdateVM vm) {
        return ResponseEntity.ok(service.update(id, vm));
    }

    @PostMapping("/patient-prescriptions/{id}/submit")
    public ResponseEntity<PatientPrescriptionDTO> submit(
            @PathVariable Long id,
            @RequestParam String lastModifiedBy
    ) {
        return ResponseEntity.ok(service.submit(id, lastModifiedBy));
    }

    @PostMapping("/patient-prescriptions/{id}/cancel")
    public ResponseEntity<PatientPrescriptionDTO> cancel(
            @PathVariable Long id,
            @RequestParam String lastModifiedBy
    ) {
        return ResponseEntity.ok(service.cancel(id, lastModifiedBy));
    }
}

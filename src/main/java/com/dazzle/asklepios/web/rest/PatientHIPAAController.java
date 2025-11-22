package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientHIPAA;
import com.dazzle.asklepios.service.PatientHIPAAService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patient.hippa.PatientHIPAAVM;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientHIPAAController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientHIPAAController.class);

    private final PatientHIPAAService hipaaService;

    // ================== CREATE ==================
    @PostMapping("/hipaa")
    public ResponseEntity<PatientHIPAA> create(@RequestBody PatientHIPAAVM vm) {
        LOG.debug("REST request to create HIPAA payload={}", vm);

        if (vm.patientId() == null) {
            throw new BadRequestAlertException("patientId is required", "hipaa", "patientId.required");
        }

        PatientHIPAA created = hipaaService.create(vm);
        return ResponseEntity.ok(created);
    }

    // ================== UPDATE ==================
    @PutMapping("/hipaa/{patientId}")
    public ResponseEntity<PatientHIPAA> update(
            @PathVariable Long patientId,
            @RequestBody PatientHIPAAVM vm
    ) {
        LOG.debug("REST request to update HIPAA for patient={} payload={}", patientId, vm);

        return hipaaService.update(patientId, vm)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    // ================== GET BY PATIENT ==================
    @GetMapping("/hipaa/{patientId}")
    public ResponseEntity<PatientHIPAA> getByPatient(@PathVariable Long patientId) {
        LOG.debug("REST request to get HIPAA for patient={}", patientId);

        return hipaaService.findByPatientId(patientId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

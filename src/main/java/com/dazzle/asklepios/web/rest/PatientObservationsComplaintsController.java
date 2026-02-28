package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientObservationsComplaints;
import com.dazzle.asklepios.service.PatientObservationsComplaintsService;
import com.dazzle.asklepios.service.dto.patientObservationsComplaints.PatientObservationsComplaintsCreateDTO;
import com.dazzle.asklepios.service.dto.patientObservationsComplaints.PatientObservationsComplaintsUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URI;

@RestController
@RequestMapping("/api/patient/observations-complaints")
@RequiredArgsConstructor
public class PatientObservationsComplaintsController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientObservationsComplaintsController.class);

    private static final String ENTITY_NAME = "patientObservationsComplaints";

    private final PatientObservationsComplaintsService patientObservationsComplaintsService;

    @PostMapping
    public ResponseEntity<PatientObservationsComplaints> create(
            @Valid @RequestBody PatientObservationsComplaintsCreateDTO dto
    ) {
        LOG.debug("[REST][CREATE] PatientObservationsComplaints payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("PatientObservationsComplaints payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        PatientObservationsComplaints saved = patientObservationsComplaintsService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/observations-complaints/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientObservationsComplaints> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientObservationsComplaintsUpdateDTO dto
    ) {
        LOG.debug("[REST][UPDATE] PatientObservationsComplaints id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException("PatientObservationsComplaints payload is required", ENTITY_NAME, "payload.required");
        }
        if (id == null) {
            throw new BadRequestAlertException("PatientObservationsComplaints id is required", ENTITY_NAME, "id.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return patientObservationsComplaintsService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientObservationsComplaints not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }



    @GetMapping("/latest/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<PatientObservationsComplaints> findLatestByEncounterId(@PathVariable Long encounterId) {
        if (encounterId == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return patientObservationsComplaintsService.findLatestByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build()); // 204
    }
}

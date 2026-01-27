package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.service.BodyMeasurementsService;
import com.dazzle.asklepios.service.dto.bodyMeasurements.BodyMeasurementsCreateDTO;
import com.dazzle.asklepios.service.dto.bodyMeasurements.BodyMeasurementsUpdateDTO;
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
@RequestMapping("/api/patient/body-measurements")
@RequiredArgsConstructor
public class BodyMeasurementsController {

    private static final Logger LOG = LoggerFactory.getLogger(BodyMeasurementsController.class);

    private static final String ENTITY_NAME = "bodyMeasurements";

    private final BodyMeasurementsService bodyMeasurementsService;

    @PostMapping
    public ResponseEntity<BodyMeasurements> create(@Valid @RequestBody BodyMeasurementsCreateDTO dto) {
        LOG.debug("[REST][CREATE] BodyMeasurements payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("BodyMeasurements payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        BodyMeasurements saved = bodyMeasurementsService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/body-measurements/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BodyMeasurements> update(
            @PathVariable Long id,
            @Valid @RequestBody BodyMeasurementsUpdateDTO dto
    ) {
        LOG.debug("[REST][UPDATE] BodyMeasurements id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException("BodyMeasurements payload is required", ENTITY_NAME, "payload.required");
        }
        if (id == null) {
            throw new BadRequestAlertException("BodyMeasurements id is required", ENTITY_NAME, "id.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return bodyMeasurementsService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "BodyMeasurements not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/latest/patient/{patientId}")
    @Transactional(readOnly = true)
    public ResponseEntity<BodyMeasurements> findLatestByPatientId(@PathVariable Long patientId) {
        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }

        return bodyMeasurementsService.findLatestByPatientId(patientId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "No body measurements found for patientId " + patientId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/latest/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<BodyMeasurements> findLatestByEncounterId(@PathVariable Long encounterId) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter id is required",
                    ENTITY_NAME,
                    "encounter.required"
            );
        }

        return bodyMeasurementsService.findLatestByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}

package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.service.VitalSignsService;
import com.dazzle.asklepios.service.dto.vitalSigns.VitalSignsCreateDTO;
import com.dazzle.asklepios.service.dto.vitalSigns.VitalSignsUpdateDTO;
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
@RequestMapping("/api/patient/vital-signs")
@RequiredArgsConstructor
public class VitalSignsController {

    private static final Logger LOG = LoggerFactory.getLogger(VitalSignsController.class);

    private static final String ENTITY_NAME = "vitalSigns";

    private final VitalSignsService vitalSignsService;

    @PostMapping
    public ResponseEntity<VitalSigns> create(@Valid @RequestBody VitalSignsCreateDTO dto) {
        LOG.debug("[REST][CREATE] VitalSigns payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("VitalSigns payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        VitalSigns saved = vitalSignsService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/vital-signs/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VitalSigns> update(
            @PathVariable Long id,
            @Valid @RequestBody VitalSignsUpdateDTO dto
    ) {
        LOG.debug("[REST][UPDATE] VitalSigns id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException("VitalSigns payload is required", ENTITY_NAME, "payload.required");
        }
        if (id == null) {
            throw new BadRequestAlertException("VitalSigns id is required", ENTITY_NAME, "id.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return vitalSignsService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "VitalSigns not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/latest/patient/{patientId}")
    @Transactional(readOnly = true)
    public ResponseEntity<VitalSigns> findLatestByPatientId(@PathVariable Long patientId) {
        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }

        return vitalSignsService.findLatestByPatientId(patientId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "No vital signs found for patientId " + patientId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/latest/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<VitalSigns> findLatestByEncounterId(@PathVariable Long encounterId) {
        if (encounterId == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return vitalSignsService.findLatestByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "No vital signs found for encounterId " + encounterId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/latest/triage/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<VitalSigns> findLatestTriageByEncounter(
            @PathVariable Long encounterId
    ) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter id is required", "vitalSigns", "encounter.required"
            );
        }

        return vitalSignsService.findLatestTriageByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "No triage vital signs found for encounterId " + encounterId,
                        "vitalSigns",
                        "notfound"
                ));
    }

}

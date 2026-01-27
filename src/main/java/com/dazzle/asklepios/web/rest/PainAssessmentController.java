package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PainAssessment;
import com.dazzle.asklepios.service.PainAssessmentService;
import com.dazzle.asklepios.service.dto.painAssessment.PainAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.painAssessment.PainAssessmentUpdateDTO;
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
@RequestMapping("/api/patient/pain-assessment")
@RequiredArgsConstructor
public class PainAssessmentController {

    private static final Logger LOG = LoggerFactory.getLogger(PainAssessmentController.class);

    private static final String ENTITY_NAME = "painAssessment";

    private final PainAssessmentService painAssessmentService;

    @PostMapping
    public ResponseEntity<PainAssessment> create(@Valid @RequestBody PainAssessmentCreateDTO dto) {
        LOG.debug("[REST][CREATE] PainAssessment payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("PainAssessment payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }
        if (dto.isActive() == null) {
            throw new BadRequestAlertException("isActive is required", ENTITY_NAME, "isActive.required");
        }

        PainAssessment saved = painAssessmentService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/pain-assessment/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PainAssessment> update(
            @PathVariable Long id,
            @Valid @RequestBody PainAssessmentUpdateDTO dto
    ) {
        LOG.debug("[REST][UPDATE] PainAssessment id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException("PainAssessment payload is required", ENTITY_NAME, "payload.required");
        }
        if (id == null) {
            throw new BadRequestAlertException("PainAssessment id is required", ENTITY_NAME, "id.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }
        if (dto.isActive() == null) {
            throw new BadRequestAlertException("isActive is required", ENTITY_NAME, "isActive.required");
        }

        return painAssessmentService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PainAssessment not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }


    @GetMapping("/latest/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<PainAssessment> findLatestByEncounterId(@PathVariable Long encounterId) {
        if (encounterId == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return painAssessmentService.findLatestByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

}

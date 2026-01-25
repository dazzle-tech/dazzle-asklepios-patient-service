package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.GeneralAssessment;
import com.dazzle.asklepios.service.GeneralAssessmentService;
import com.dazzle.asklepios.service.dto.generalAssessment.GeneralAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.generalAssessment.GeneralAssessmentUpdateDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/patient")
public class GeneralAssessmentController {

    private static final Logger LOG = LoggerFactory.getLogger(GeneralAssessmentController.class);

    private final GeneralAssessmentService generalAssessmentService;

    public GeneralAssessmentController(GeneralAssessmentService generalAssessmentService) {
        this.generalAssessmentService = generalAssessmentService;
    }

    /**
     * {@code POST /general-assessment} : Create a new GeneralAssessment.
     */
    @PostMapping("/general-assessment")
    public ResponseEntity<GeneralAssessment> create(@Valid @RequestBody GeneralAssessmentCreateDTO generalAssessmentCreateDTO) {
        LOG.debug("REST create GeneralAssessment payload={}", generalAssessmentCreateDTO);
        GeneralAssessment created = generalAssessmentService.create(generalAssessmentCreateDTO);
        return ResponseEntity
                .created(URI.create("/api/emergency/general-assessment/" + created.getId()))
                .body(created);
    }

    /**
     * {@code PUT /general-assessment/{id}} : Update an existing GeneralAssessment.
     */
    @PutMapping("/general-assessment/{id}")
    public ResponseEntity<GeneralAssessment> update(@PathVariable Long id, @Valid @RequestBody GeneralAssessmentUpdateDTO dto) {
        LOG.debug("REST update GeneralAssessment id={} payload={}", id, dto);
        if (dto.id() == null || !id.equals(dto.id())) {
            return ResponseEntity.badRequest().build();
        }
        GeneralAssessment updated = generalAssessmentService.update(dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * {@code GET /general-assessment/encounter/{encounterId}/latest} : Get latest GeneralAssessment for encounter.
     */
    @GetMapping("/general-assessment/encounter/{encounterId}/latest")
    public ResponseEntity<GeneralAssessment> getLatestByEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get latest GeneralAssessment by encounterId={}", encounterId);
        return ResponseEntity.ok(generalAssessmentService.getLatestByEncounterId(encounterId));
    }

    /**
     * {@code GET /general-assessment/encounter/{encounterId}/latest-triage} : Get latest triage GeneralAssessment for encounter.
     */
    @GetMapping("/general-assessment/encounter/{encounterId}/latest-triage")
    public ResponseEntity<GeneralAssessment> getLatestTriageByEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get latest triage GeneralAssessment by encounterId={}", encounterId);
        return ResponseEntity.ok(generalAssessmentService.getLatestTriageByEncounterId(encounterId));
    }

    @DeleteMapping("/general-assessment/{id}")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        LOG.debug("REST hard delete GeneralAssessment id={}", id);
        generalAssessmentService.hardDelete(id);
        return ResponseEntity.noContent().build();
    }
}
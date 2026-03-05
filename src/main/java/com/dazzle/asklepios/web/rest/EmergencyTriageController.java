package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.EmergencyTriage;
import com.dazzle.asklepios.service.EmergencyTriageService;
import com.dazzle.asklepios.service.dto.emergencyTriage.EmergencyTriageCreateDTO;
import com.dazzle.asklepios.service.dto.emergencyTriage.EmergencyTriageDestinationUpdateDTO;
import com.dazzle.asklepios.service.dto.emergencyTriage.EmergencyTriageLevelAssessmentUpdateDTO;
import com.dazzle.asklepios.service.dto.emergencyTriage.EmergencyTriageUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/patient")
public class EmergencyTriageController {

    private static final Logger LOG = LoggerFactory.getLogger(EmergencyTriageController.class);
    private static final String ENTITY_NAME = "EmergencyTriage";

    private final EmergencyTriageService emergencyTriageService;

    public EmergencyTriageController(EmergencyTriageService emergencyTriageService) {
        this.emergencyTriageService = emergencyTriageService;
    }

    /**
     * {@code POST /emergency-triage} : Create emergency triage record once per encounter (upsert-by-encounter).
     *
     * <p>If a record already exists for the encounter, returns it (does not create a new one).</p>
     */
    @PostMapping("/emergency-triage")
    public ResponseEntity<EmergencyTriage> createOrGet(@Valid @RequestBody EmergencyTriageCreateDTO dto) {
        LOG.debug("REST createOrGet EmergencyTriage payload={}", dto);
        EmergencyTriage triage = emergencyTriageService.createOrGetByEncounter(dto);
        return ResponseEntity.ok(triage);
    }

    /**
     * {@code GET /emergency-triage/encounter/{encounterId}/latest} : Get latest triage for encounter.
     */
    @GetMapping("/emergency-triage/encounter/{encounterId}/latest")
    public ResponseEntity<Optional<EmergencyTriage>> getLatestByEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get latest EmergencyTriage by encounterId={}", encounterId);
        return ResponseEntity.ok(emergencyTriageService.getLatestByEncounterId(encounterId));
    }

    /**
     * {@code PUT /emergency-triage/{id}/eye-assessment} : Update eye/pupil/HPI section.
     */
    @PutMapping("/emergency-triage/{id}/eye-assessment")
    public ResponseEntity<EmergencyTriage> updateEyeAssessment(@PathVariable Long id, @Valid @RequestBody EmergencyTriageUpdateDTO dto) {
        LOG.debug("REST update eye assessment EmergencyTriage id={} payload={}", id, dto);
        if (dto.id() == null || !id.equals(dto.id())) {
            throw new BadRequestAlertException(
                    "Invalid id", ENTITY_NAME, "idinvalid"
            );
        }
        return ResponseEntity.ok(emergencyTriageService.updateEyeAssessment(dto));
    }

    /**
     * {@code PUT /emergency-triage/{id}/level-assessment} : Update triage level assessment + required services.
     */
    @PutMapping("/emergency-triage/{id}/level-assessment")
    public ResponseEntity<EmergencyTriage> updateLevelAssessment(@PathVariable Long id, @Valid @RequestBody EmergencyTriageLevelAssessmentUpdateDTO dto) {
        LOG.debug("REST update level assessment EmergencyTriage id={} payload={}", id, dto);
        if (dto.id() == null || !id.equals(dto.id())) {
            throw new BadRequestAlertException(
                    "Invalid id", ENTITY_NAME, "idinvalid"
            );
        }
        return ResponseEntity.ok(emergencyTriageService.updateLevelAssessment(dto));
    }

    /**
     * {@code PUT /emergency-triage/{id}/destination} : Update destination only.
     */
    @PutMapping("/emergency-triage/{id}/destination")
    public ResponseEntity<EmergencyTriage> updateDestination(@PathVariable Long id, @Valid @RequestBody EmergencyTriageDestinationUpdateDTO dto) {
        LOG.debug("REST update destination EmergencyTriage id={} payload={}", id, dto);
        if (dto.id() == null || !id.equals(dto.id())) {
            throw new BadRequestAlertException(
                    "Invalid id", ENTITY_NAME, "idinvalid"
            );
        }
        return ResponseEntity.ok(emergencyTriageService.updateDestination(dto));
    }

    /**
     * {@code DELETE /emergency-triage/{id}} : Hard delete.
     */
    @DeleteMapping("/emergency-triage/{id}")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        LOG.debug("REST hard delete EmergencyTriage id={}", id);
        emergencyTriageService.hardDelete(id);
        return ResponseEntity.noContent().build();
    }
}
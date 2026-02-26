package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.EncounterAssessment;
import com.dazzle.asklepios.service.EncounterAssessmentService;
import com.dazzle.asklepios.service.dto.encounterAssessment.EncounterAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.encounterAssessment.EncounterAssessmentUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/patient")
public class EncounterAssessmentController {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAssessmentController.class);

    private final EncounterAssessmentService encounterAssessmentService;

    public EncounterAssessmentController(EncounterAssessmentService encounterAssessmentService) {
        this.encounterAssessmentService = encounterAssessmentService;
    }

    @PostMapping("/encounter-assessments")
    public ResponseEntity<EncounterAssessment> create(
            @Valid @RequestBody EncounterAssessmentCreateDTO createRequest
    ) {
        LOG.debug("REST create EncounterAssessment payload={}", createRequest);

        if (createRequest == null) {
            throw new BadRequestAlertException(
                    "EncounterAssessment payload is required",
                    "encounterAssessment",
                    "payload.required"
            );
        }

        EncounterAssessment created = encounterAssessmentService.create(createRequest);

        return ResponseEntity
                .created(URI.create("/api/patient/encounter-assessments/" + created.getId()))
                .body(created);
    }

    @PutMapping("/encounter-assessments/{id}")
    public ResponseEntity<EncounterAssessment> update(
            @PathVariable Long id,
            @Valid @RequestBody EncounterAssessmentUpdateDTO updateRequest
    ) {
        LOG.debug("REST update EncounterAssessment id={} payload={}", id, updateRequest);

        if (updateRequest == null) {
            throw new BadRequestAlertException(
                    "EncounterAssessment payload is required",
                    "encounterAssessment",
                    "payload.required"
            );
        }

        if (updateRequest.id() == null || !updateRequest.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "encounterAssessment",
                    "id.mismatch"
            );
        }

        EncounterAssessment updated = encounterAssessmentService.update(id, updateRequest);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/encounter-assessments/latest")
    public ResponseEntity<EncounterAssessment> getLatest(
            @RequestParam("encounterId") Long encounterId,
            @RequestParam("userId") Long userId
    ) {
        LOG.debug("REST get latest EncounterAssessment encounterId={} userId={}", encounterId, userId);

        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "encounterId is required",
                    "encounterAssessment",
                    "encounterId.required"
            );
        }

        if (userId == null) {
            throw new BadRequestAlertException(
                    "userId is required",
                    "encounterAssessment",
                    "userId.required"
            );
        }

        EncounterAssessment latest =
                encounterAssessmentService.findLatestByEncounterIdAndUserId(encounterId, userId);

        return ResponseEntity.ok(latest);
    }

}

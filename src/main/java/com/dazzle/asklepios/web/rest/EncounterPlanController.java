package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.EncounterPlan;
import com.dazzle.asklepios.service.EncounterPlanService;
import com.dazzle.asklepios.service.dto.encounterPlan.EncounterPlanCreateDTO;
import com.dazzle.asklepios.service.dto.encounterPlan.EncounterPlanUpdateDTO;
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
public class EncounterPlanController {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterPlanController.class);

    private final EncounterPlanService encounterPlanService;

    public EncounterPlanController(EncounterPlanService encounterPlanService) {
        this.encounterPlanService = encounterPlanService;
    }

    @PostMapping("/encounter-plans")
    public ResponseEntity<EncounterPlan> create(
            @Valid @RequestBody EncounterPlanCreateDTO dto
    ) {
        LOG.debug("REST create EncounterPlan payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "EncounterPlan payload is required",
                    "encounterPlan",
                    "payload.required"
            );
        }

        EncounterPlan created = encounterPlanService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/encounter-plans/" + created.getId()))
                .body(created);
    }

    @PutMapping("/encounter-plans/{id}")
    public ResponseEntity<EncounterPlan> update(
            @PathVariable Long id,
            @Valid @RequestBody EncounterPlanUpdateDTO dto
    ) {
        LOG.debug("REST update EncounterPlan id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "EncounterPlan payload is required",
                    "encounterPlan",
                    "payload.required"
            );
        }

        if (dto.id() == null || !dto.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "encounterPlan",
                    "id.mismatch"
            );
        }

        EncounterPlan updated = encounterPlanService.update(id, dto);

        return ResponseEntity.ok(updated);
    }


    @GetMapping("/encounter-plans/latest")
    public ResponseEntity<EncounterPlan> getLatest(
            @RequestParam("encounterId") Long encounterId
    ) {
        LOG.debug("REST get latest EncounterPlan encounterId={}", encounterId);

        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "encounterId is required",
                    "encounterPlan",
                    "encounterId.required"
            );
        }

        EncounterPlan latest =
                encounterPlanService.findLatestByEncounterId(encounterId);

        return ResponseEntity.ok(latest);
    }

}

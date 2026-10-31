package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.RandomBloodSugar;
import com.dazzle.asklepios.service.RandomBloodSugarService;
import com.dazzle.asklepios.service.dto.randomBloodSugar.RandomBloodSugarCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.observations.RandomBloodSugarFieldHistoryVM;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@Validated
public class RandomBloodSugarController {

    private static final Logger LOG = LoggerFactory.getLogger(RandomBloodSugarController.class);
    private static final String ENTITY_NAME = "randomBloodSugar";

    private final RandomBloodSugarService randomBloodSugarService;

    @PostMapping("/random-blood-sugar")
    public ResponseEntity<RandomBloodSugar> create(@Valid @RequestBody RandomBloodSugarCreateDTO dto) {
        LOG.debug("[REST][CREATE] RandomBloodSugar payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("RandomBloodSugar payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        RandomBloodSugar saved = randomBloodSugarService.create(dto);
        return ResponseEntity
                .created(URI.create("/api/patient/random-blood-sugar/" + saved.getId()))
                .body(saved);
    }

    @GetMapping("/random-blood-sugar/latest/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<RandomBloodSugar> findLatestByEncounterId(
            @PathVariable @NotNull Long encounterId
    ) {
        return randomBloodSugarService.findLatestByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/random-blood-sugar/encounter/{encounterId}/history")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RandomBloodSugarFieldHistoryVM>> findHistoryByEncounter(
            @PathVariable @NotNull Long encounterId
    ) {
        LOG.debug("[REST][FIND HISTORY BY ENCOUNTER] encounterId={}", encounterId);
        return ResponseEntity.ok(randomBloodSugarService.findFieldHistoryByEncounterId(encounterId));
    }
}

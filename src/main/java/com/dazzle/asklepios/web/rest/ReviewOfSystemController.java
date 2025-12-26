package com.dazzle.asklepios.web.rest;


import com.dazzle.asklepios.domain.ReviewOfSystem;
import com.dazzle.asklepios.service.ReviewOfSystemService;
import com.dazzle.asklepios.service.dto.medicalsheets.reviewofsystem.ReviewOfSystemCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.reviewofsystem.ReviewOfSystemUpdateDTO;
import com.dazzle.asklepios.web.rest.vm.reviewofsystem.ReviewOfSystemResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class ReviewOfSystemController {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewOfSystemController.class);

    private final ReviewOfSystemService reviewOfSystemService;

    public ReviewOfSystemController(ReviewOfSystemService reviewOfSystemService) {
        this.reviewOfSystemService = reviewOfSystemService;
    }

    /** Create (upsert by encounterId+bodySystem+systemDetail) */
    @PostMapping("/review-of-system")
    public ResponseEntity<ReviewOfSystemResponseVM> create(@Valid @RequestBody ReviewOfSystemCreateDTO dto) {
        LOG.debug("REST create ReviewOfSystem payload={}", dto);
        ReviewOfSystem saved = reviewOfSystemService.create(dto);
        return ResponseEntity
                .created(URI.create("/api/encounter/review-of-system/" + saved.getId()))
                .body(ReviewOfSystemResponseVM.ofEntity(saved));
    }

    /** Update by id */
    @PutMapping("/review-of-system/{id}")
    public ResponseEntity<ReviewOfSystemResponseVM> update(@PathVariable Long id, @Valid @RequestBody ReviewOfSystemUpdateDTO dto) {
        LOG.debug("REST update ReviewOfSystem id={} payload={}", id, dto);

        ReviewOfSystem updated = reviewOfSystemService.update(dto);
        return ResponseEntity.ok(ReviewOfSystemResponseVM.ofEntity(updated));
    }

    /** Get by id */
    @GetMapping("/review-of-system/{id}")
    public ResponseEntity<ReviewOfSystemResponseVM> getById(@PathVariable Long id) {
        ReviewOfSystem ros = reviewOfSystemService.findOne(id);
        return ResponseEntity.ok(ReviewOfSystemResponseVM.ofEntity(ros));
    }

    /** Get all by encounter */
    @GetMapping("/{encounterId}/review-of-system")
    public ResponseEntity<List<ReviewOfSystemResponseVM>> getByEncounter(@PathVariable Long encounterId) {
        List<ReviewOfSystemResponseVM> list = reviewOfSystemService.findByEncounter(encounterId)
                .stream().map(ReviewOfSystemResponseVM::ofEntity).toList();
        return ResponseEntity.ok(list);
    }

    /** Get by encounter + bodySystem */
    @GetMapping("/{encounterId}/review-of-system/{bodySystem}")
    public ResponseEntity<List<ReviewOfSystemResponseVM>> getByEncounterAndSystem(
            @PathVariable Long encounterId,
            @PathVariable String bodySystem
    ) {
        List<ReviewOfSystemResponseVM> list = reviewOfSystemService.findByEncounterAndBodySystem(encounterId, bodySystem)
                .stream().map(ReviewOfSystemResponseVM::ofEntity).toList();
        return ResponseEntity.ok(list);
    }

    /** Hard delete by unique key (unchecked) */
    @DeleteMapping("/{encounterId}/review-of-system/{bodySystem}/{systemDetail}")
    public ResponseEntity<Void> deleteByUnique(
            @PathVariable Long encounterId,
            @PathVariable String bodySystem,
            @PathVariable String systemDetail
    ) {
        reviewOfSystemService.deleteByUnique(encounterId, bodySystem, systemDetail);
        return ResponseEntity.noContent().build();
    }

    /** Hard delete by id */
    @DeleteMapping("/review-of-system/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewOfSystemService.delete(id);
        return ResponseEntity.noContent().build();
    }

}


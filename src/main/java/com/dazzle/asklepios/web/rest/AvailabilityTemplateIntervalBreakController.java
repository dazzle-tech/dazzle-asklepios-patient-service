package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.AvailabilityTemplateIntervalBreak;
import com.dazzle.asklepios.service.AvailabilityTemplateIntervalBreakService;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateIntervalBreak.AvailabilityTemplateIntervalBreakCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.availabilityTemplate.AvailabilityTemplateIntervalBreakResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class AvailabilityTemplateIntervalBreakController {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityTemplateIntervalBreakController.class);

    private final AvailabilityTemplateIntervalBreakService availabilityTemplateIntervalBreakService;

    private static final String ENTITY_NAME = "availabilityTemplateIntervalBreak";

    public AvailabilityTemplateIntervalBreakController(
            AvailabilityTemplateIntervalBreakService availabilityTemplateIntervalBreakService
    ) {
        this.availabilityTemplateIntervalBreakService = availabilityTemplateIntervalBreakService;
    }

    /**
     * {@code POST /availability-template-interval-breaks} : Create a new interval break.
     *
     * @param dto the break data
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and the created break
     */
    @PostMapping("/availability-template-interval-breaks")
    public ResponseEntity<AvailabilityTemplateIntervalBreakResponseVM> createIntervalBreak(@Valid @RequestBody AvailabilityTemplateIntervalBreakCreateDTO dto) {
        LOG.debug("REST request to create AvailabilityTemplateIntervalBreak : {}", dto);
        if (dto == null) {
            throw new BadRequestAlertException("payload.required", ENTITY_NAME, "Break payload is required");
        }
        AvailabilityTemplateIntervalBreak result = availabilityTemplateIntervalBreakService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/availability-template-interval-breaks/" + result.getId()))
                .body(AvailabilityTemplateIntervalBreakResponseVM.of(result));
    }

    /**
     * {@code DELETE /availability-template-interval-breaks/:id} : Hard delete break by id.
     *
     * @param id the id of the break
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/availability-template-interval-breaks/{id}")
    public ResponseEntity<Void> deleteIntervalBreak(@PathVariable Long id) {
        LOG.debug("REST request to hard delete AvailabilityTemplateIntervalBreak : {}", id);

        if (id == null) {
            throw new BadRequestAlertException("Break id is required", ENTITY_NAME, "id.required");
        }
        availabilityTemplateIntervalBreakService.hardDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET /availability-template-interval-breaks/by-interval/:intervalId} : get all breaks by interval id.
     *
     * @param intervalId the interval id
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of breaks
     */
    @GetMapping("/availability-template-interval-breaks/by-interval/{intervalId}")
    public ResponseEntity<List<AvailabilityTemplateIntervalBreakResponseVM>> getByInterval(@PathVariable Long intervalId) {
        LOG.debug("REST request to get AvailabilityTemplateIntervalBreaks by intervalId : {}", intervalId);
        if (intervalId == null) {
            throw new BadRequestAlertException("Interval id is required", ENTITY_NAME, "interval.required");
        }
        List<AvailabilityTemplateIntervalBreakResponseVM> result = availabilityTemplateIntervalBreakService
                .findByInterval(intervalId)
                .stream()
                .map(AvailabilityTemplateIntervalBreakResponseVM::of)
                .toList();

        return ResponseEntity.ok(result);
    }
}
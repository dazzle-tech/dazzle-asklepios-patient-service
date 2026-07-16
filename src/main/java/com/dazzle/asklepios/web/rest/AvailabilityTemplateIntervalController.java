package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.service.AvailabilityTemplateIntervalService;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateInterval.AvailabilityTemplateIntervalCreateDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateInterval.AvailabilityTemplateIntervalUpdateDTO;
import com.dazzle.asklepios.web.rest.vm.availabilityTemplate.AvailabilityTemplateIntervalResponseVM;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class AvailabilityTemplateIntervalController {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityTemplateIntervalController.class);

    private final AvailabilityTemplateIntervalService availabilityTemplateIntervalService;

    public AvailabilityTemplateIntervalController(
            AvailabilityTemplateIntervalService availabilityTemplateIntervalService
    ) {
        this.availabilityTemplateIntervalService = availabilityTemplateIntervalService;
    }

    /**
     * {@code POST /api/patient/availability-template-intervals} : Create a new availability template interval.
     *
     * @param dto the creation payload
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and body the created interval
     */
    @PostMapping("/availability-template-intervals")
    public ResponseEntity<AvailabilityTemplateIntervalResponseVM> createInterval(@Valid @RequestBody AvailabilityTemplateIntervalCreateDTO dto) {
        LOG.debug("REST request to create AvailabilityTemplateInterval : {}", dto);

        AvailabilityTemplateIntervalResponseVM templateIntervalResponseVM = AvailabilityTemplateIntervalResponseVM.ofEntity(
                availabilityTemplateIntervalService.create(dto)
        );

        return ResponseEntity
                .created(URI.create("/api/patient/availability-template-intervals/" + templateIntervalResponseVM.id()))
                .body(templateIntervalResponseVM);
    }

    /**
     * {@code PUT /api/patient/availability-template-intervals/:id} : Update an existing interval.
     *
     * @param id the id of the interval to update
     * @param dto the update payload
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and body the updated interval
     */
    @PutMapping("/availability-template-intervals/{id}")
    public ResponseEntity<AvailabilityTemplateIntervalResponseVM> updateInterval(@PathVariable Long id, @Valid @RequestBody AvailabilityTemplateIntervalUpdateDTO dto) {
        LOG.debug("REST request to update AvailabilityTemplateInterval id={} payload={}", id, dto);

        return availabilityTemplateIntervalService.update(id, dto)
                .map(AvailabilityTemplateIntervalResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    /**
     * {@code GET /api/patient/availability-template-intervals/:id} : Get one interval by id.
     *
     * @param id the id of the interval
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and body the interval
     */
    @GetMapping("/availability-template-intervals/{id}")
    public ResponseEntity<AvailabilityTemplateIntervalResponseVM> getOne(@PathVariable Long id) {
        LOG.debug("REST request to get AvailabilityTemplateInterval : {}", id);

        return availabilityTemplateIntervalService.findOne(id)
                .map(AvailabilityTemplateIntervalResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    /**
     * {@code GET /api/patient/availability-template-intervals/search?templateId=...&dayOfWeek=...}
     * : Get intervals by template id and day of week.
     *
     * @param templateId the template id
     * @param dayOfWeek the day of week
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and body the list of intervals
     */
    @GetMapping("/availability-template-intervals/search")
    public ResponseEntity<List<AvailabilityTemplateIntervalResponseVM>> getByTemplateAndDayOfWeek(@RequestParam Long templateId, @RequestParam DayOfWeek dayOfWeek) {
        LOG.debug("REST request to get AvailabilityTemplateIntervals by templateId={} and dayOfWeek={}", templateId, dayOfWeek);

        List<AvailabilityTemplateIntervalResponseVM> result = availabilityTemplateIntervalService.findByTemplateAndDayOfWeek(templateId, dayOfWeek)
                        .stream()
                        .map(AvailabilityTemplateIntervalResponseVM::ofEntity)
                        .toList();

        return ResponseEntity.ok(result);
    }


    /**
     * {@code DELETE /api/patient/availability-template-intervals/:id} : Hard delete interval by id.
     *
     * @param id the id of the interval
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/availability-template-intervals/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("REST request to delete AvailabilityTemplateInterval : {}", id);

        availabilityTemplateIntervalService.hardDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code POST /api/patient/availability-template-intervals/{id}/apply-to-all-working-days}
     * : Apply an interval and its breaks to all working days.
     *
     * @param id the interval id
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @PostMapping("/availability-template-intervals/{id}/apply-to-all-working-days")
    public ResponseEntity<Void> applyToAllWorkingDays(@PathVariable Long id) {
        LOG.debug("REST request to apply AvailabilityTemplateInterval {} to all working days", id);

        availabilityTemplateIntervalService.applyToAllWorkingDays(id);

        return ResponseEntity.noContent().build();
    }
}
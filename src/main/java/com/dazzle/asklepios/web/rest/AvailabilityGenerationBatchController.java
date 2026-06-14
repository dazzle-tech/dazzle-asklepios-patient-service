package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.service.AvailabilityGenerationBatchService;
import com.dazzle.asklepios.service.dto.availabilityGenerationBatch.AvailabilityGenerationBatchApplyDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.availabilityGenerationBatch.ApplyAvailabilityTemplateResponseVM;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class AvailabilityGenerationBatchController {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityGenerationBatchController.class);

    private final AvailabilityGenerationBatchService availabilityGenerationBatchService;

    /**
     * Apply template and generate free appointments.
     */
    @PostMapping("/availability-generation-batches/apply")
    public ResponseEntity<ApplyAvailabilityTemplateResponseVM> applyTemplate(
            @Valid @RequestBody AvailabilityGenerationBatchApplyDTO request
    ) {
        LOG.debug("REST request to apply availability template: {}", request);
        validateRequest(request);

        ApplyAvailabilityTemplateResponseVM response = availabilityGenerationBatchService.applyTemplate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get generation batches for parent template and its child templates.
     */
    @GetMapping("/availability-generation-batches/template/{templateId}")
    public ResponseEntity<List<AvailabilityGenerationBatch>> getListByParentTemplate(@PathVariable Long templateId, @ParameterObject Pageable pageable) {
        LOG.debug("REST request to get availability generation batches by parent template: {}", templateId);
        Page<AvailabilityGenerationBatch> batches =
                availabilityGenerationBatchService.getListByParentTemplate(templateId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                batches
        );
        return new ResponseEntity<>(batches.getContent(), headers, HttpStatus.OK);
    }

    private void validateRequest(AvailabilityGenerationBatchApplyDTO request) {
        if (request.templateId() == null) {
            throw new BadRequestAlertException("Template id is required", "availabilityTemplate", "templateidnull");
        }

        if (request.startDate() == null || request.endDate() == null) {
            throw new BadRequestAlertException("Start date and end date are required", "availabilityTemplate", "daterangenull");
        }

        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestAlertException("End date cannot be before start date", "availabilityTemplate", "invaliddaterange");
        }

        if (Boolean.TRUE.equals(request.deferred()) && request.deferredAt() == null) {
            throw new BadRequestAlertException("Deferred at is required when deferred is true", "availabilityTemplate", "deferredatnull");
        }
    }

    @GetMapping("/availability-generation-batches/{id}")
    public ResponseEntity<AvailabilityGenerationBatch> getBatchById(
            @PathVariable("id") @NotNull Long batchId
    ) {
        LOG.debug("REST get Batch by id={}", batchId);

        AvailabilityGenerationBatch batch = availabilityGenerationBatchService.getById(batchId);
        return ResponseEntity.ok(batch);
    }

    /**
     * Get generation batches for selected template, excluding selected batch.
     */
    @GetMapping("/availability-generation-batches/template/{templateId}/exclude/{batchId}")
    public ResponseEntity<List<AvailabilityGenerationBatch>> getListByTemplateExcludingBatch(@PathVariable Long templateId, @PathVariable Long batchId, @ParameterObject Pageable pageable) {
        LOG.debug("REST request to get availability generation batches by template: {}, excluding batch: {}", templateId, batchId);

        Page<AvailabilityGenerationBatch> batches =
                availabilityGenerationBatchService.getListByTemplateExcludingBatch(templateId, batchId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                batches
        );

        return new ResponseEntity<>(batches.getContent(), headers, HttpStatus.OK);
    }

}
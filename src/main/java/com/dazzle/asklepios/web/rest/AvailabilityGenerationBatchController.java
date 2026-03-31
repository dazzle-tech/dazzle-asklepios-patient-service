package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.service.AvailabilityGenerationBatchService;
import com.dazzle.asklepios.service.dto.availabilityGenerationBatch.AvailabilityGenerationBatchApplyDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.availabilityGenerationBatch.ApplyAvailabilityTemplateResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApplyAvailabilityTemplateResponseDTO> applyTemplate(
            @Valid @RequestBody AvailabilityGenerationBatchApplyDTO request
    ) {
        LOG.debug("REST request to apply availability template: {}", request);
        validateRequest(request);

        ApplyAvailabilityTemplateResponseDTO response = availabilityGenerationBatchService.applyTemplate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get generation batches for parent template and its child templates.
     */
    @GetMapping("/availability-generation-batches/template/{templateId}")
    public ResponseEntity<List<AvailabilityGenerationBatch>> getListByParentTemplate(@PathVariable Long templateId) {
        LOG.debug("REST request to get availability generation batches by parent template: {}", templateId);
        List<AvailabilityGenerationBatch> batches =
                availabilityGenerationBatchService.getListByParentTemplate(templateId);
        return ResponseEntity.ok(batches);
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

}
package com.dazzle.asklepios.integration.nahdi.controller;

import com.dazzle.asklepios.integration.nahdi.dto.NahdiSendResult;
import com.dazzle.asklepios.integration.nahdi.dto.NahdiSyncStatus;
import com.dazzle.asklepios.integration.nahdi.dto.dispensing.NahdiDispensingSummary;
import com.dazzle.asklepios.integration.nahdi.service.NahdiIntegrationService;
import com.dazzle.asklepios.integration.nahdi.service.dispensing.NahdiDispensingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
public class NahdiPrescriptionController {

    private static final Logger LOG = LoggerFactory.getLogger(NahdiPrescriptionController.class);

    private final NahdiIntegrationService nahdiIntegrationService;
    private final NahdiDispensingService nahdiDispensingService;

    public NahdiPrescriptionController(NahdiIntegrationService nahdiIntegrationService, NahdiDispensingService nahdiDispensingService) {
        this.nahdiIntegrationService = nahdiIntegrationService;
        this.nahdiDispensingService = nahdiDispensingService;
    }

    /**
     * POST /nahdi/prescriptions/{id}/resend : sends the current state of a submitted prescription to Nahdi again,
     * with the same reference, after the doctor finished editing it.
     */
    @PostMapping("/nahdi/prescriptions/{id}/resend")
    public ResponseEntity<NahdiSendResult> resend(@PathVariable Long id) {
        LOG.debug("REST request to resend prescription to Nahdi id={}", id);
        return ResponseEntity.ok(nahdiIntegrationService.resendPrescription(id));
    }

    /**
     * POST /nahdi/prescriptions/{id}/dispensing-status/refresh : fetches which medications the pharmacy dispensed
     * and stores it; dispensed medications cannot be edited or cancelled afterwards.
     */
    @PostMapping("/nahdi/prescriptions/{id}/dispensing-status/refresh")
    public ResponseEntity<NahdiDispensingSummary> refreshDispensingStatus(@PathVariable Long id) {
        LOG.debug("REST request to refresh Nahdi dispensing status of prescription id={}", id);
        return ResponseEntity.ok(nahdiDispensingService.refresh(id));
    }

    /**
     * GET /nahdi/prescriptions/{id}/sync-status : whether the prescription has changes not sent to Nahdi yet.
     */
    @GetMapping("/nahdi/prescriptions/{id}/sync-status")
    public ResponseEntity<NahdiSyncStatus> syncStatus(@PathVariable Long id) {
        LOG.debug("REST request to get Nahdi sync status of prescription id={}", id);
        return ResponseEntity.ok(nahdiIntegrationService.syncStatus(id));
    }
}

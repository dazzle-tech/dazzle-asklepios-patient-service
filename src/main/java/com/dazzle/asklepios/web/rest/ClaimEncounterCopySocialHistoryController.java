package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterCopySocialHistory;
import com.dazzle.asklepios.service.ClaimEncounterCopySocialHistoryService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySocialHistoryCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySocialHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySocialHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient/billing/claim-encounter-copy-social-histories")
public class ClaimEncounterCopySocialHistoryController {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClaimEncounterCopySocialHistoryController.class);

    private final ClaimEncounterCopySocialHistoryService service;

    public ClaimEncounterCopySocialHistoryController(
            ClaimEncounterCopySocialHistoryService service
    ) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterCopySocialHistory> update(
            @PathVariable Long id,
            @Valid @RequestBody ClaimEncounterCopySocialHistoryUpdateDTO dto
    ) {
        LOG.debug(
                "REST update ClaimEncounterCopySocialHistory id={} payload={}",
                id,
                dto
        );

        ClaimEncounterCopySocialHistory existing = service.findById(id);

        if (existing.getStatus() == com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled social history cannot be updated",
                    "claimEncounterCopySocialHistory",
                    "already.cancelled"
            );
        }

        ClaimEncounterCopySocialHistory updated =
                service.update(id, dto);

        LOG.info(
                "REST update ClaimEncounterCopySocialHistory - updated id={}",
                updated.getId()
        );

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterCopySocialHistory> cancel(
            @PathVariable Long id,
            @Valid @RequestBody ClaimEncounterCopySocialHistoryCancelDTO dto
    ) {
        LOG.debug(
                "REST cancel ClaimEncounterCopySocialHistory id={} payload={}",
                id,
                dto
        );

        ClaimEncounterCopySocialHistory existing = service.findById(id);

        if (existing.getStatus() == com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Social history already cancelled",
                    "claimEncounterCopySocialHistory",
                    "already.cancelled"
            );
        }

        ClaimEncounterCopySocialHistory cancelled =
                service.cancel(id, dto.cancellationReason());

        LOG.info(
                "REST cancel ClaimEncounterCopySocialHistory - cancelled id={}",
                cancelled.getId()
        );

        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/by-copy/{claimEncounterCopyId}/not-cancelled")
    public ResponseEntity<List<ClaimEncounterCopySocialHistory>> findByCopyNotCancelled(
            @PathVariable Long claimEncounterCopyId
    ) {
        LOG.debug(
                "REST list ClaimEncounterCopySocialHistory by copy (not cancelled) copyId={}",
                claimEncounterCopyId
        );

        List<ClaimEncounterCopySocialHistory> result =
                service.findByClaimEncounterCopyIdNotCancelled(
                        claimEncounterCopyId
                );

        LOG.info(
                "REST list ClaimEncounterCopySocialHistory by copy (not cancelled) - returned {} items",
                result.size()
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-copy/{claimEncounterCopyId}/all")
    public ResponseEntity<List<ClaimEncounterCopySocialHistory>> findByCopyAll(
            @PathVariable Long claimEncounterCopyId
    ) {
        LOG.debug(
                "REST list ClaimEncounterCopySocialHistory by copy (all) copyId={}",
                claimEncounterCopyId
        );

        List<ClaimEncounterCopySocialHistory> result =
                service.findByClaimEncounterCopyId(claimEncounterCopyId);

        LOG.info(
                "REST list ClaimEncounterCopySocialHistory by copy (all) - returned {} items",
                result.size()
        );

        return ResponseEntity.ok(result);
    }
    @PostMapping("/by-copy/{claimEncounterCopyId}")
    public ResponseEntity<ClaimEncounterCopySocialHistory> create(
            @PathVariable Long claimEncounterCopyId,
            @Valid @RequestBody ClaimEncounterCopySocialHistoryCreateDTO dto
    ) {
        return ResponseEntity.ok(
                service.create(claimEncounterCopyId, dto)
        );
    }
}
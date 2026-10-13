package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterProgressNote;
import com.dazzle.asklepios.service.ClaimEncounterProgressNoteService;
import com.dazzle.asklepios.service.dto.progressNotes.ClaimEncounterProgressNoteCancelDTO;
import com.dazzle.asklepios.service.dto.progressNotes.ClaimEncounterProgressNoteUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient/billing/claim-encounter-progress-notes")
public class ClaimEncounterProgressNoteController {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClaimEncounterProgressNoteController.class);

    private final ClaimEncounterProgressNoteService service;

    public ClaimEncounterProgressNoteController(
            ClaimEncounterProgressNoteService service
    ) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterProgressNote> update(
            @PathVariable Long id,
            @Valid @RequestBody ClaimEncounterProgressNoteUpdateDTO dto
    ) {
        LOG.debug(
                "REST update ClaimEncounterProgressNote id={} payload={}",
                id,
                dto
        );

        ClaimEncounterProgressNote existing = service.findById(id);

        if (existing.getCancelledDate() != null) {
            throw new BadRequestAlertException(
                    "Cancelled progress note cannot be updated",
                    "claimEncounterProgressNote",
                    "already.cancelled"
            );
        }

        ClaimEncounterProgressNote updated =
                service.update(id, dto);

        LOG.info(
                "REST update ClaimEncounterProgressNote - updated id={}",
                updated.getId()
        );

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterProgressNote> cancel(
            @PathVariable Long id,
            @Valid @RequestBody ClaimEncounterProgressNoteCancelDTO dto
    ) {
        LOG.debug(
                "REST cancel ClaimEncounterProgressNote id={} payload={}",
                id,
                dto
        );

        ClaimEncounterProgressNote existing = service.findById(id);

        if (existing.getCancelledDate() != null) {
            throw new BadRequestAlertException(
                    "Progress note already cancelled",
                    "claimEncounterProgressNote",
                    "already.cancelled"
            );
        }

        ClaimEncounterProgressNote cancelled =
                service.cancel(id, dto.cancellationReason());

        LOG.info(
                "REST cancel ClaimEncounterProgressNote - cancelled id={}",
                cancelled.getId()
        );

        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/by-copy/{claimEncounterCopyId}/not-cancelled")
    public ResponseEntity<List<ClaimEncounterProgressNote>> findByCopyNotCancelled(
            @PathVariable Long claimEncounterCopyId
    ) {
        LOG.debug(
                "REST list ClaimEncounterProgressNote by copy (not cancelled) copyId={}",
                claimEncounterCopyId
        );

        List<ClaimEncounterProgressNote> result =
                service.findByClaimEncounterCopyIdNotCancelled(
                        claimEncounterCopyId
                );

        LOG.info(
                "REST list ClaimEncounterProgressNote by copy (not cancelled) - returned {} items",
                result.size()
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-copy/{claimEncounterCopyId}/all")
    public ResponseEntity<List<ClaimEncounterProgressNote>> findByCopyAll(
            @PathVariable Long claimEncounterCopyId
    ) {
        LOG.debug(
                "REST list ClaimEncounterProgressNote by copy (all) copyId={}",
                claimEncounterCopyId
        );

        List<ClaimEncounterProgressNote> result =
                service.findByClaimEncounterCopyId(claimEncounterCopyId);

        LOG.info(
                "REST list ClaimEncounterProgressNote by copy (all) - returned {} items",
                result.size()
        );

        return ResponseEntity.ok(result);
    }
}
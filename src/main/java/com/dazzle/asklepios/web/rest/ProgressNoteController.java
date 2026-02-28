package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ProgressNote;
import com.dazzle.asklepios.service.ProgressNoteService;
import com.dazzle.asklepios.service.dto.progressNotes.ProgressNoteCancelDTO;
import com.dazzle.asklepios.service.dto.progressNotes.ProgressNoteCreateDTO;
import com.dazzle.asklepios.service.dto.progressNotes.ProgressNoteUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.ProgressNoteLogVM;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/patient")
public class ProgressNoteController {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProgressNoteController.class);

    private final ProgressNoteService service;

    public ProgressNoteController(ProgressNoteService service) {
        this.service = service;
    }

    @PostMapping("/progress-notes")
    public ResponseEntity<ProgressNote> create(
            @Valid @RequestBody ProgressNoteCreateDTO dto
    ) {
        LOG.debug("REST create ProgressNote payload={}", dto);
        ProgressNote created = service.create(dto);
        LOG.info("REST create ProgressNote - created id={}", created.getId());
        return ResponseEntity
                .created(URI.create("/api/patient/progress-notes/" + created.getId()))
                .body(created);
    }

    @PutMapping("/progress-notes/{id}")
    public ResponseEntity<ProgressNote> update(
            @PathVariable Long id,
            @Valid @RequestBody ProgressNoteUpdateDTO dto
    ) {
        LOG.debug("REST update ProgressNote id={} payload={}", id, dto);
        ProgressNote existing = service.findById(id);
        if (existing.getCancelledDate() != null) {
            throw new BadRequestAlertException(
                    "Cancelled progress note cannot be updated",
                    "progressNote",
                    "already.cancelled"
            );
        }
        ProgressNote updated = service.update(id, dto);
        LOG.info("REST update ProgressNote - updated id={}", updated.getId());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/progress-notes/{id}/cancel")
    public ResponseEntity<ProgressNote> cancel(
            @PathVariable Long id,
            @Valid @RequestBody ProgressNoteCancelDTO dto
    ) {
        LOG.debug("REST cancel ProgressNote id={} payload={}", id, dto);
        ProgressNote existing = service.findById(id);
        if (existing.getCancelledDate() != null) {
            throw new BadRequestAlertException(
                    "Progress note already cancelled",
                    "progressNote",
                    "already.cancelled"
            );
        }
        ProgressNote cancelled = service.cancel(id, dto.cancellationReason(), dto.cancelledBy());
        LOG.info("REST cancel ProgressNote - cancelled id={}", cancelled.getId());
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/progress-notes/by-encounter/{encounterId}/not-cancelled")
    public ResponseEntity<List<ProgressNote>> findByEncounterNotCancelled(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list ProgressNote by encounter (not cancelled) encounterId={} pageable={}", encounterId, pageable);
        Page<ProgressNote> page =
                service.findByEncounterNotCancelled(encounterId, pageable);
        LOG.info("REST list ProgressNote by encounter (not cancelled) - returned {} items", page.getContent().size());
        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(), page
                );
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/progress-notes/by-encounter/{encounterId}/all")
    public ResponseEntity<List<ProgressNote>> findByEncounterAll(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list ProgressNote by encounter (all) encounterId={} pageable={}", encounterId, pageable);
        Page<ProgressNote> page =
                service.findByEncounterAll(encounterId, pageable);
        LOG.info("REST list ProgressNote by encounter (all) - returned {} items", page.getContent().size());
        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(), page
                );
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/progress-notes/{id}/logs")
    public ResponseEntity<List<ProgressNoteLogVM>> findLogs(
            @PathVariable Long id
    ) {
        LOG.debug("REST get ProgressNote logs id={}", id);
        List<ProgressNoteLogVM> result =
                service.findLogsByProgressNoteId(id)
                        .stream()
                        .map(log -> new ProgressNoteLogVM(
                                log.getId(),
                                log.getAction(),
                                log.getCreatedBy(),
                                log.getCreatedDate(),
                                log.getLastModifiedBy(),
                                log.getLastModifiedDate(),
                                log.getPayload()
                        ))
                        .toList();
        LOG.info("REST get ProgressNote logs - returned {} logs", result.size());
        return ResponseEntity.ok(result);
    }
}
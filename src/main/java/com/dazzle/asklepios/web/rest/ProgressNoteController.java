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

@RestController
@RequestMapping("/api/patient/progress-notes")
public class ProgressNoteController {

    private final ProgressNoteService service;

    public ProgressNoteController(ProgressNoteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProgressNote> create(
            @Valid @RequestBody ProgressNoteCreateDTO dto
    ) {
        ProgressNote created = service.create(dto);
        return ResponseEntity
                .created(URI.create("/api/patient/progress-notes/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProgressNote> update(
            @PathVariable Long id,
            @Valid @RequestBody ProgressNoteUpdateDTO dto
    ) {
        ProgressNote existing = service.findById(id);

        if (existing.getCancelledDate() != null) {
            throw new BadRequestAlertException(
                    "Cancelled progress note cannot be updated",
                    "progressNote",
                    "already.cancelled"
            );
        }

        return ResponseEntity.ok(service.update(id, dto));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ProgressNote> cancel(
            @PathVariable Long id,
            @Valid @RequestBody ProgressNoteCancelDTO dto
    ) {
        ProgressNote existing = service.findById(id);

        if (existing.getCancelledDate() != null) {
            throw new BadRequestAlertException(
                    "Progress note already cancelled",
                    "progressNote",
                    "already.cancelled"
            );
        }

        return ResponseEntity.ok(
                service.cancel(id, dto.cancellationReason(), dto.cancelledBy())
        );
    }

    @GetMapping("/by-encounter/{encounterId}")
    public ResponseEntity<List<ProgressNote>> findByEncounter(
            @PathVariable Long encounterId,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @ParameterObject Pageable pageable
    ) {
        Page<ProgressNote> page =
                service.findByEncounter(encounterId, includeCancelled, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(), page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<ProgressNoteLogVM>> findLogs(
            @PathVariable Long id
    ) {
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

        return ResponseEntity.ok(result);
    }

}

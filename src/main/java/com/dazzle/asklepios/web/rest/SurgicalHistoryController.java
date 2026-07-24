package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.SurgicalHistory;
import com.dazzle.asklepios.service.SurgicalHistoryService;
import com.dazzle.asklepios.service.dto.surgicalHistory.SurgicalHistoryCancelDTO;
import com.dazzle.asklepios.service.dto.surgicalHistory.SurgicalHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.surgicalHistory.SurgicalHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/patient")
public class SurgicalHistoryController {

    private static final Logger LOG =
            LoggerFactory.getLogger(SurgicalHistoryController.class);

    private final SurgicalHistoryService service;

    public SurgicalHistoryController(SurgicalHistoryService service) {
        this.service = service;
    }

    @PostMapping("/surgical-history")
    public ResponseEntity<SurgicalHistory> create(
            @Valid @RequestBody SurgicalHistoryCreateDTO dto
    ) {
        LOG.debug("REST create SurgicalHistory payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "Surgical history payload is required",
                    "surgicalHistory",
                    "payload.required"
            );
        }

        SurgicalHistory created = service.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/surgical-history/" + created.getId()))
                .body(created);
    }

    @PutMapping("/surgical-history")
    public ResponseEntity<SurgicalHistory> update(
            @Valid @RequestBody SurgicalHistoryUpdateDTO dto
    ) {
        SurgicalHistory updated = service.update(dto);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/surgical-history/cancel")
    public ResponseEntity<SurgicalHistory> cancel(
            @Valid @RequestBody SurgicalHistoryCancelDTO dto
    ) {
        SurgicalHistory cancelled = service.cancel(dto);
        return ResponseEntity.ok(cancelled);
    }

    @DeleteMapping("/surgical-history/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/surgical-history")
    public ResponseEntity<List<SurgicalHistory>> list(
            @RequestParam Long patientId,
            @RequestParam(defaultValue = "false") boolean showCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list SurgicalHistory patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        Page<SurgicalHistory> page =
                service.findByPatientId(
                        patientId,
                        showCancelled,
                        pageable
                );

        HttpHeaders headers =
                com.dazzle.asklepios.web.rest.Helper.PaginationUtil
                        .generatePaginationHttpHeaders(
                                ServletUriComponentsBuilder.fromCurrentRequest(),
                                page
                        );

        List<SurgicalHistory> body = page.getContent();

        return new ResponseEntity<>(
                body,
                headers,
                HttpStatus.OK
        );
    }
}
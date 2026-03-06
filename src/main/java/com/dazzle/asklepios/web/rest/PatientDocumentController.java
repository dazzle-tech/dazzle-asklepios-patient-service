package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.service.PatientDocumentService;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientDocumentCreateDTO;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientDocumentUpdateDTO;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientNoDocumentCreateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientDocumentController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientDocumentController.class);

    private final PatientDocumentService patientDocumentService;

    public PatientDocumentController(PatientDocumentService patientDocumentService) {
        this.patientDocumentService = patientDocumentService;
    }

    @PostMapping("/documents")
    public ResponseEntity<PatientDocument> createPatientDocument(
            @Valid @RequestBody PatientDocumentCreateDTO dto
    ) {
        LOG.debug("REST create PatientDocument payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientDocument payload is required",
                    "patientDocument",
                    "payload.required"
            );
        }

        if (dto.type() == DocumentType.NO_DOCUMENT) {
            throw new BadRequestAlertException(
                    "Use /documents/no-document endpoint for NO_DOCUMENT",
                    "patientDocument",
                    "invalidType"
            );
        }

        PatientDocument created = patientDocumentService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/documents/" + created.getId()))
                .body(created);
    }

    @PostMapping("/documents/no-document")
    public ResponseEntity<PatientDocument> createNoDocument(
            @Valid @RequestBody PatientNoDocumentCreateDTO dto
    ) {
        LOG.debug("REST create Patient NO_DOCUMENT payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientDocument payload is required",
                    "patientDocument",
                    "payload.required"
            );
        }

        if (dto.type() != DocumentType.NO_DOCUMENT) {
            throw new BadRequestAlertException(
                    "Document type must be NO_DOCUMENT",
                    "patientDocument",
                    "invalidType"
            );
        }

        PatientDocument created = patientDocumentService.createNoDocument(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/documents/" + created.getId()))
                .body(created);
    }


    @PutMapping("/documents/{id}")
    public ResponseEntity<PatientDocument> updatePatientDocument(
            @PathVariable Long id,
            @Valid @RequestBody PatientDocumentUpdateDTO dto
    ) {
        LOG.debug("REST update PatientDocument id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientDocument payload is required",
                    "patientDocument",
                    "payload.required"
            );
        }

        PatientDocument updated = patientDocumentService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/documents/patient/{patientId}")
    public ResponseEntity<List<PatientDocument>> getDocumentsByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientDocuments by patientId={} pageable={}", patientId, pageable);

        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient id is required",
                    "patientDocument",
                    "patient.required"
            );
        }

        Page<PatientDocument> page =
                patientDocumentService.getDocumentsByPatient(patientId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/documents/patient/{patientId}/primary")
    public ResponseEntity<PatientDocument> getPrimaryDocumentByPatient(
            @PathVariable @NotNull Long patientId
    ) {
        LOG.debug("REST get primary PatientDocument by patientId={}", patientId);
        PatientDocument document = patientDocumentService.getPrimaryDocumentByPatientId(patientId);
        return ResponseEntity.ok(document);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deletePatientDocument(@PathVariable Long id) {
        LOG.debug("REST delete PatientDocument id={}", id);

        boolean deleted = patientDocumentService.delete(id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
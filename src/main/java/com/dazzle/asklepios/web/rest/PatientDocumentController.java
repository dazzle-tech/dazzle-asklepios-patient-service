package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.service.PatientDocumentService;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientDocumentCreateDTO;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientDocumentUpdateDTO;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientNoDocumentCreateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientDocument.PatientDocumentResponseVM;
import jakarta.validation.Valid;
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
    public ResponseEntity<PatientDocumentResponseVM> createPatientDocument(
            @Valid @RequestBody PatientDocumentCreateDTO dto
    ) {
        LOG.debug("REST create PatientDocument payload={}", dto);

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
                .body(PatientDocumentResponseVM.ofEntity(created));
    }


    @PostMapping("/documents/no-document")
    public ResponseEntity<PatientDocumentResponseVM> createNoDocument(
            @Valid @RequestBody PatientNoDocumentCreateDTO dto
    ) {
        LOG.debug("REST create Patient NO_DOCUMENT payload={}", dto);

        if (dto.type() != DocumentType.NO_DOCUMENT) {
            throw new BadRequestAlertException(
                    "Document type must be NO_DOCUMENT",
                    "patientDocument",
                    "invalidType"
            );
        }

        PatientDocument created = patientDocumentService.create(
                new PatientDocumentCreateDTO(
                        dto.patientId(),
                        null,
                        DocumentType.NO_DOCUMENT,
                        null,
                        dto.isPrimary()
                )
        );

        return ResponseEntity
                .created(URI.create("/api/patient/documents/" + created.getId()))
                .body(PatientDocumentResponseVM.ofEntity(created));
    }


    @PutMapping("/documents/{id}")
    public ResponseEntity<PatientDocumentResponseVM> updatePatientDocument(
            @PathVariable Long id,
            @Valid @RequestBody PatientDocumentUpdateDTO dto
    ) {
        LOG.debug("REST update PatientDocument id={} payload={}", id, dto);

        return patientDocumentService.update(id, dto)
                .map(PatientDocumentResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @GetMapping("/documents")
    public ResponseEntity<List<PatientDocumentResponseVM>> getAllPatientDocuments(
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientDocuments pageable={}", pageable);

        Page<PatientDocument> page = patientDocumentService.findAll(pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        List<PatientDocumentResponseVM> body = page.getContent()
                .stream()
                .map(PatientDocumentResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }


    @GetMapping("/documents/patient/{patientId}")
    public ResponseEntity<List<PatientDocumentResponseVM>> getDocumentsByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list PatientDocuments by patientId={} pageable={}",
                patientId,
                pageable
        );

        Page<PatientDocument> page =
                patientDocumentService.getDocumentsByPatient(patientId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        List<PatientDocumentResponseVM> body = page.getContent()
                .stream()
                .map(PatientDocumentResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
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

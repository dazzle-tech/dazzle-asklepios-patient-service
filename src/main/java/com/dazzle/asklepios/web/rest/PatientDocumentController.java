package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.service.PatientDocumentService;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientDocument.PatientDocumentCreateVM;
import com.dazzle.asklepios.web.rest.vm.patientDocument.PatientDocumentResponseVM;
import com.dazzle.asklepios.web.rest.vm.patientDocument.PatientDocumentUpdateVM;
import com.dazzle.asklepios.web.rest.vm.patientDocument.PatientNoDocumentCreateVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
            @Valid @RequestBody PatientDocumentCreateVM vm
    ) {
        LOG.debug("REST create PatientDocument payload={}", vm);

        if (vm.type() == DocumentType.NO_DOCUMENT) {
            throw new BadRequestAlertException(
                    "Use /documents/no-document endpoint for NO_DOCUMENT",
                    "patientDocument",
                    "invalidType"
            );
        }

        PatientDocument toCreate = PatientDocument.builder()
                .countryId(vm.countryId())
                .type(vm.type())
                .number(vm.number())
                .isPrimary(Boolean.TRUE.equals(vm.isPrimary()))
                .build();

        PatientDocument created = patientDocumentService.create(vm.patientId(), toCreate);

        return ResponseEntity
                .created(URI.create("/api/patient/documents/" + created.getId()))
                .body(PatientDocumentResponseVM.ofEntity(created));
    }
    @PostMapping("/documents/no-document")
    public ResponseEntity<PatientDocumentResponseVM> createNoDocument(
            @Valid @RequestBody PatientNoDocumentCreateVM vm
    ) {
        LOG.debug("REST create Patient NO_DOCUMENT payload={}", vm);

        if (vm.type() != DocumentType.NO_DOCUMENT) {
            throw new BadRequestAlertException(
                    "Document type must be NO_DOCUMENT",
                    "patientDocument",
                    "invalidType"
            );
        }

        PatientDocument toCreate = PatientDocument.builder()
                .type(DocumentType.NO_DOCUMENT)
                .isPrimary(Boolean.TRUE.equals(vm.isPrimary()))
                .build();

        PatientDocument created = patientDocumentService.create(vm.patientId(), toCreate);

        return ResponseEntity
                .created(URI.create("/api/patient/documents/" + created.getId()))
                .body(PatientDocumentResponseVM.ofEntity(created));
    }



    @PutMapping("/documents/{id}")
    public ResponseEntity<PatientDocumentResponseVM> updatePatientDocument(
            @PathVariable Long id,
            @Valid @RequestBody PatientDocumentUpdateVM vm
    ) {
        LOG.debug("REST update PatientDocument id={} payload={}", id, vm);

        PatientDocument patch = new PatientDocument();
        patch.setCountryId(vm.countryId());
        patch.setType(vm.type());
        patch.setNumber(vm.number());
        patch.setIsPrimary(Boolean.TRUE.equals(vm.isPrimary()));

        return patientDocumentService.update(id, vm.patientId(), patch)
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
                ServletUriComponentsBuilder.fromCurrentRequest(), page
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
        LOG.debug("REST list ALL PatientDocuments patientId={} pageable={}", patientId, pageable);

        Page<PatientDocument> page = patientDocumentService.getDocumentsByPatient(patientId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
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
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

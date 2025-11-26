package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.service.PatientDocumentService;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.patientDocument.PatientDocumentCreateVM;
import com.dazzle.asklepios.web.rest.vm.patientDocument.PatientDocumentResponseVM;
import com.dazzle.asklepios.web.rest.vm.patientDocument.PatientDocumentUpdateVM;
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
@RequestMapping("/api/patient-documents")
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

        PatientDocument toCreate = PatientDocument.builder()
                .countryId(vm.countryId())
                .category(vm.category())
                .type(vm.type())
                .number(vm.number())
                .build();

        PatientDocument created = patientDocumentService.create(vm.patientId(), toCreate);
        PatientDocumentResponseVM body = PatientDocumentResponseVM.ofEntity(created);

        return ResponseEntity
                .created(URI.create("/api/patient-documents/documents/" + created.getId()))
                .body(body);
    }

    @PutMapping("/documents/{id}")
    public ResponseEntity<PatientDocumentResponseVM> updatePatientDocument(
            @PathVariable Long id,
            @Valid @RequestBody PatientDocumentUpdateVM vm
    ) {
        LOG.debug("REST update PatientDocument id={} payload={}", id, vm);

        PatientDocument patch = new PatientDocument();
        patch.setCountryId(vm.countryId());
        patch.setCategory(vm.category());
        patch.setType(vm.type());
        patch.setNumber(vm.number());

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

    @GetMapping("/documents/secondary/{patientId}")
    public ResponseEntity<List<PatientDocumentResponseVM>> getSecondaryDocumentsByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list SECONDARY PatientDocuments patientId={} pageable={}", patientId, pageable);

        Page<PatientDocument> page = patientDocumentService.getSecondaryDocumentsByPatient(patientId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<PatientDocumentResponseVM> body = page.getContent()
                .stream()
                .map(PatientDocumentResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/documents/primary/{patientId}")
    public ResponseEntity<List<PatientDocumentResponseVM>> getPrimaryDocumentsByPatient(
            @PathVariable Long patientId
    ) {
        LOG.debug("REST list PRIMARY PatientDocuments patientId={}", patientId);

        List<PatientDocumentResponseVM> body = patientDocumentService
                .getPrimaryDocumentsByPatient(patientId)
                .stream()
                .map(PatientDocumentResponseVM::ofEntity)
                .toList();

        return ResponseEntity.ok(body);
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

package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.service.PatientRelationService;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.relation.PatientRelationCreateVM;
import com.dazzle.asklepios.web.rest.vm.relation.PatientRelationResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient/patient-relations")
public class PatientRelationController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientRelationController.class);

    private final PatientRelationService service;

    public PatientRelationController(PatientRelationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PatientRelationResponseVM> create(
            @Valid @RequestBody PatientRelationCreateVM vm
    ) {
        LOG.debug("REST create PatientRelation payload={}", vm);
        PatientRelation saved = service.create(vm.toEntity());
        PatientRelationResponseVM body = PatientRelationResponseVM.fromEntity(saved);
        LOG.debug("REST create PatientRelation response={}", body);

        return ResponseEntity
                .created(URI.create("/api/setup/patient-relations/" + saved.getId()))
                .body(body);
    }
    @PutMapping("/{id}")
    public ResponseEntity<PatientRelationResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientRelationCreateVM vm
    ) {
        LOG.debug("REST update PatientRelation id={} payload={}", id, vm);

        PatientRelation updated = service.update(id, vm.toEntity());

        PatientRelationResponseVM body = PatientRelationResponseVM.fromEntity(updated);
        LOG.debug("REST update PatientRelation response={}", body);

        return ResponseEntity.ok(body);
    }


    @GetMapping
    public ResponseEntity<List<PatientRelationResponseVM>> findAll(@ParameterObject Pageable pageable) {
        LOG.debug("REST list PatientRelations page={}", pageable);

        Page<PatientRelation> page = service.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<PatientRelationResponseVM> body = page.getContent()
                .stream()
                .map(PatientRelationResponseVM::fromEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/by-patient/{patientId}")
    public ResponseEntity<List<PatientRelationResponseVM>> findByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientRelations by patientId={} page={}", patientId, pageable);

        Page<PatientRelation> page = service.findByPatientId(patientId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<PatientRelationResponseVM> body = page.getContent()
                .stream()
                .map(PatientRelationResponseVM::fromEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("REST delete PatientRelation id={}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

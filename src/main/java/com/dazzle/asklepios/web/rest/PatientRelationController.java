package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.service.PatientRelationService;
import com.dazzle.asklepios.service.dto.relation.PatientRelationCreateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.relation.PatientRelationResponseVM;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientRelationController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientRelationController.class);

    private final PatientRelationService service;

    public PatientRelationController(PatientRelationService service) {
        this.service = service;
    }

    @PostMapping("/patient-relations")
    public ResponseEntity<PatientRelationResponseVM> create(
            @Valid @RequestBody PatientRelationCreateDTO dto
    ) {
        LOG.debug("REST create PatientRelation payload={}", dto);
        PatientRelation saved = service.create(dto.toEntity());
        PatientRelationResponseVM body = PatientRelationResponseVM.fromEntity(saved);
        LOG.debug("REST create PatientRelation response={}", body);

        return ResponseEntity
                .created(URI.create("/api/setup/patient-relations/" + saved.getId()))
                .body(body);
    }

    @PutMapping("/patient-relations/{id}")
    public ResponseEntity<PatientRelationResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientRelationCreateDTO dto
    ) {
        LOG.debug("REST update PatientRelation id={} payload={}", id, dto);

        PatientRelation updated = service.update(id, dto.toEntity());

        PatientRelationResponseVM body = PatientRelationResponseVM.fromEntity(updated);
        LOG.debug("REST update PatientRelation response={}", body);

        return ResponseEntity.ok(body);
    }


    @GetMapping("/patient-relations")
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

    @GetMapping("/patient-relations/by-patient/{patientId}")
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

    @DeleteMapping("/patient-relations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("REST delete PatientRelation id={}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import com.dazzle.asklepios.service.PatientPreferredHealthProfessionalService;
import com.dazzle.asklepios.service.PatientService;
import com.dazzle.asklepios.service.dto.patientPreferredHealthProfessional.PatientPreferredHealthProfessionalCreateDTO;
import com.dazzle.asklepios.service.dto.patientPreferredHealthProfessional.PatientPreferredHealthProfessionalUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientPreferredHealthProfessionalResponseVM;
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
public class PatientPreferredHealthProfessionalController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPreferredHealthProfessionalController.class);

    private final PatientPreferredHealthProfessionalService service;
    private final PatientService patientService;

    public PatientPreferredHealthProfessionalController(
            PatientPreferredHealthProfessionalService service,
            PatientService patientService
    ) {
        this.service = service;
        this.patientService = patientService;
    }

    @PostMapping("/preferred-health-professionals/patient/{patientId}")
    public ResponseEntity<PatientPreferredHealthProfessionalResponseVM> createPreferredHealthProfessionalForPatient(
            @PathVariable Long patientId,
            @Valid @RequestBody PatientPreferredHealthProfessionalCreateDTO dto
    ) {
        LOG.debug("REST create PatientPreferredHealthProfessional for patientId={} payload={}", patientId, dto);

        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient id is required",
                    "patientPreferredHealthProfessional",
                    "patient.required"
            );
        }

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientPreferredHealthProfessional payload is required",
                    "patientPreferredHealthProfessional",
                    "payload.required"
            );
        }

        Patient patient = patientService.findById(patientId);

        PatientPreferredHealthProfessional created = service.create(patient, dto);

        PatientPreferredHealthProfessionalResponseVM body =
                PatientPreferredHealthProfessionalResponseVM.ofEntity(created);

        return ResponseEntity
                .created(URI.create("/api/patient/preferred-health-professionals/" + created.getId()))
                .body(body);
    }

    @PutMapping("/preferred-health-professionals/{id}")
    public ResponseEntity<PatientPreferredHealthProfessionalResponseVM> updatePreferredHealthProfessional(
            @PathVariable Long id,
            @Valid @RequestBody PatientPreferredHealthProfessionalUpdateDTO dto
    ) {
        LOG.debug("REST update PatientPreferredHealthProfessional id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientPreferredHealthProfessional payload is required",
                    "patientPreferredHealthProfessional",
                    "payload.required"
            );
        }

        if (dto.id() == null || !dto.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "patientPreferredHealthProfessional",
                    "id.mismatch"
            );
        }

        PatientPreferredHealthProfessional existing = service.findByIdOrThrow(id);
        PatientPreferredHealthProfessional updated = service.update(existing, dto);

        PatientPreferredHealthProfessionalResponseVM body =
                PatientPreferredHealthProfessionalResponseVM.ofEntity(updated);

        return ResponseEntity.ok(body);
    }

    @GetMapping("/preferred-health-professionals/patient/{patientId}")
    public ResponseEntity<List<PatientPreferredHealthProfessionalResponseVM>> getPreferredHealthProfessionalsByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientPreferredHealthProfessional for patientId={} pageable={}", patientId, pageable);

        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient id is required",
                    "patientPreferredHealthProfessional",
                    "patient.required"
            );
        }

        Page<PatientPreferredHealthProfessional> page = service.findAllByPatient(patientId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<PatientPreferredHealthProfessionalResponseVM> body = page.getContent()
                .stream()
                .map(PatientPreferredHealthProfessionalResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/preferred-health-professionals/{id}")
    public ResponseEntity<Void> deletePreferredHealthProfessional(@PathVariable Long id) {
        LOG.debug("REST delete PatientPreferredHealthProfessional id={}", id);

        service.findByIdOrThrow(id);
        service.hardDelete(id);

        return ResponseEntity.noContent().build();
    }
}
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.service.PatientDiagnosisService;
import com.dazzle.asklepios.service.dto.patientDiagnosis.PatientDiagnosisCreateDTO;
import com.dazzle.asklepios.service.dto.patientDiagnosis.PatientDiagnosisUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping("/api/patient")
public class PatientDiagnosisController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientDiagnosisController.class);

    private final PatientDiagnosisService patientDiagnosisService;

    public PatientDiagnosisController(PatientDiagnosisService patientDiagnosisService) {
        this.patientDiagnosisService = patientDiagnosisService;
    }

    @PostMapping("/patient-diagnoses")
    public ResponseEntity<PatientDiagnosis> create(
            @Valid @RequestBody PatientDiagnosisCreateDTO dto
    ) {
        LOG.debug("REST create PatientDiagnosis payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientDiagnosis payload is required",
                    "patientDiagnosis",
                    "payload.required"
            );
        }

        PatientDiagnosis created = patientDiagnosisService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/patient-diagnoses/" + created.getId()))
                .body(created);
    }

    @PutMapping("/patient-diagnoses/{id}")
    public ResponseEntity<PatientDiagnosis> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientDiagnosisUpdateDTO dto
    ) {
        LOG.debug("REST update PatientDiagnosis id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientDiagnosis payload is required",
                    "patientDiagnosis",
                    "payload.required"
            );
        }

        if (dto.id() == null || !dto.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "patientDiagnosis",
                    "id.mismatch"
            );
        }

        PatientDiagnosis updated = patientDiagnosisService.update(id, dto);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/patient-diagnoses/latest")
    public ResponseEntity<PatientDiagnosis> getLatest(
            @RequestParam("encounterId") Long encounterId
    ) {
        LOG.debug("REST get latest PatientDiagnosis encounterId={}", encounterId);

        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "encounterId is required",
                    "patientDiagnosis",
                    "encounterId.required"
            );
        }

        PatientDiagnosis latest =
                patientDiagnosisService.findLatestByEncounterId(encounterId);

        return ResponseEntity.ok(latest);
    }

    @GetMapping("/patient-diagnoses/patient/{patientId}")
    public ResponseEntity<List<PatientDiagnosis>> listByPatientId(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientDiagnoses by patientId={} pageable={}", patientId, pageable);

        if (patientId == null) {
            throw new BadRequestAlertException(
                    "patientId is required",
                    "patientDiagnosis",
                    "patientId.required"
            );
        }

        Page<PatientDiagnosis> page = patientDiagnosisService.findByPatientId(patientId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

}

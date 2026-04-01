package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.service.PatientDiagnosisService;
import com.dazzle.asklepios.service.dto.patientDiagnosis.PatientDiagnosisCreateDTO;
import com.dazzle.asklepios.service.dto.patientDiagnosis.PatientDiagnosisUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientDiagnosis.PatientDiagnosisFlagVM;
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

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patient")
public class
PatientDiagnosisController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientDiagnosisController.class);

    private final PatientDiagnosisService patientDiagnosisService;

    public PatientDiagnosisController(PatientDiagnosisService patientDiagnosisService) {
        this.patientDiagnosisService = patientDiagnosisService;
    }

    @PostMapping("/patient-diagnoses")
    public ResponseEntity<PatientDiagnosis> create(
            @Valid @RequestBody PatientDiagnosisCreateDTO createRequest
    ) {
        LOG.debug("REST create PatientDiagnosis payload={}", createRequest);

        if (createRequest == null) {
            throw new BadRequestAlertException(
                    "PatientDiagnosis payload is required",
                    "patientDiagnosis",
                    "payload.required"
            );
        }

        PatientDiagnosis created = patientDiagnosisService.create(createRequest);

        return ResponseEntity
                .created(URI.create("/api/patient/patient-diagnoses/" + created.getId()))
                .body(created);
    }

    @PutMapping("/patient-diagnoses/{id}")
    public ResponseEntity<PatientDiagnosis> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientDiagnosisUpdateDTO updateRequest
    ) {
        LOG.debug("REST update PatientDiagnosis id={} payload={}", id, updateRequest);

        if (updateRequest == null) {
            throw new BadRequestAlertException(
                    "PatientDiagnosis payload is required",
                    "patientDiagnosis",
                    "payload.required"
            );
        }

        if (updateRequest.id() == null || !updateRequest.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "patientDiagnosis",
                    "id.mismatch"
            );
        }

        PatientDiagnosis updated = patientDiagnosisService.update(id, updateRequest);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/patient-diagnoses/by-encounter/{encounterId}")
    public ResponseEntity<List<PatientDiagnosis>> getByEncounterId(
            @PathVariable @NotNull Long encounterId
    ) {
        LOG.debug("REST get PatientDiagnosis by encounterId={}", encounterId);

        List<PatientDiagnosis> diagnoses = patientDiagnosisService.getByEncounterId(encounterId);

        return ResponseEntity.ok(diagnoses);
    }

    @GetMapping("/patient-diagnoses/by-encounter/{encounterId}/primary")
    public ResponseEntity<PatientDiagnosis> getPrimaryByEncounterId(
            @PathVariable @NotNull Long encounterId
    ) {
        LOG.debug("REST get primary PatientDiagnosis by encounterId={}", encounterId);

        PatientDiagnosis primaryDiagnosis =
                patientDiagnosisService.getPrimaryDiagnosisByEncounterId(encounterId);

        return ResponseEntity.ok(primaryDiagnosis);
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

    @DeleteMapping("/patient-diagnoses/{id}/hard")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        LOG.debug("REST hard delete PatientDiagnosis id={}", id);

        if (id == null) {
            throw new BadRequestAlertException(
                    "id is required",
                    "patientDiagnosis",
                    "id.required"
            );
        }

        patientDiagnosisService.hardDelete(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/patient-diagnosis/exists/{encounterId}")
    public ResponseEntity<Boolean> existsByEncounterId(@PathVariable Long encounterId) {

        LOG.debug("REST request to check if PatientDiagnosis exists for encounterId={}", encounterId);

        boolean exists = patientDiagnosisService.existsByEncounterId(encounterId);

        return ResponseEntity.ok(exists);
    }

    @GetMapping("/patient-diagnoses/flags/by-encounters")
    public ResponseEntity<List<PatientDiagnosisFlagVM>> getPrimaryDiagnosisFlagsByEncounterIds(
            @RequestParam List<Long> encounterIds
    ) {
        LOG.debug("REST get PRIMARY PatientDiagnosis flags by encounterIds={}", encounterIds);

        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BadRequestAlertException(
                    "encounterIds are required",
                    "patientDiagnosis",
                    "encounterIds.required"
            );
        }

        Set<PatientDiagnosis> diagnoses =
                patientDiagnosisService.findPrimaryByEncounterIds(encounterIds);

        Set<Long> diagnosisEncounterIds = diagnoses.stream()
                .map(PatientDiagnosis::getEncounterId)
                .collect(Collectors.toSet());

        List<PatientDiagnosisFlagVM> result = encounterIds.stream()
                .map(id -> PatientDiagnosisFlagVM.of(
                        id,
                        diagnosisEncounterIds.contains(id) // hasPrimaryDiagnoses
                ))
                .toList();

        return ResponseEntity.ok(result);
    }
}

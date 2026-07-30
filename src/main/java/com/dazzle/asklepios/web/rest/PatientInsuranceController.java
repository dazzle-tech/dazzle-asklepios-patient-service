package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.service.PatientInsuranceService;
import com.dazzle.asklepios.service.dto.patientInsurance.PatientInsuranceCreateDTO;
import com.dazzle.asklepios.service.dto.patientInsurance.PatientInsuranceUpdateDTO;
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
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientInsuranceController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientInsuranceController.class);

    private final PatientInsuranceService patientInsuranceService;

    public PatientInsuranceController(PatientInsuranceService patientInsuranceService) {
        this.patientInsuranceService = patientInsuranceService;
    }

    @PostMapping("/insurances")
    public ResponseEntity<PatientInsurance> createPatientInsurance(
            @Valid @RequestBody PatientInsuranceCreateDTO dto
    ) {
        LOG.debug("REST create PatientInsurance payload={}", dto);

        if (dto == null) {
            LOG.warn("[CREATE] PatientInsurance rejected: payload is null");
            throw new BadRequestAlertException(
                    "PatientInsurance payload is required",
                    "patientInsurance",
                    "payload.required"
            );
        }

        PatientInsurance created = patientInsuranceService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/insurances/" + created.getId()))
                .body(created);
    }

    @PutMapping("/insurances/{id}")
    public ResponseEntity<PatientInsurance> updatePatientInsurance(
            @PathVariable Long id,
            @Valid @RequestBody PatientInsuranceUpdateDTO dto
    ) {
        LOG.debug("REST update PatientInsurance id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientInsurance payload is required",
                    "patientInsurance",
                    "payload.required"
            );
        }

        if (dto.id() == null || !dto.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "patientInsurance",
                    "id.mismatch"
            );
        }

        PatientInsurance updated = patientInsuranceService.update(id, dto);

        return ResponseEntity.ok(updated);
    }
    
    @GetMapping("/insurances/patient/{patientId}")
    public ResponseEntity<List<PatientInsurance>> getInsurancesByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientInsurances by patientId={} pageable={}", patientId, pageable);

        if (patientId == null) {
            LOG.warn("[FIND_BY_PATIENT] PatientInsurance rejected: patientId is null");
            throw new BadRequestAlertException(
                    "Patient id is required",
                    "patientInsurance",
                    "patient.required"
            );
        }

        Page<PatientInsurance> page = patientInsuranceService.getInsurancesByPatient(patientId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/insurances")
    public ResponseEntity<List<PatientInsurance>> getAllInsurances(
            @ParameterObject Pageable pageable
    ) {
        Page<PatientInsurance> page = patientInsuranceService.findAll(pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @DeleteMapping("/insurances/{id}")
    public ResponseEntity<Void> deletePatientInsurance(
            @PathVariable Long id,
            @RequestParam(name = "deleteCoverages", defaultValue = "false") boolean deleteCoverages
    ) {
        LOG.debug("REST delete PatientInsurance id={} deleteCoverages={}", id, deleteCoverages);

        boolean deleted = patientInsuranceService.delete(id, deleteCoverages);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/insurances/{id}/coverages/count")
    public ResponseEntity<Long> countInsuranceCoverages(@PathVariable Long id) {
        LOG.debug("REST count coverages insuranceId={}", id);
        return ResponseEntity.ok(patientInsuranceService.countCoverages(id));
    }
}

package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.service.PatientInsuranceService;
import com.dazzle.asklepios.service.dto.patientInsurance.PatientInsuranceCreateDTO;
import com.dazzle.asklepios.service.dto.patientInsurance.PatientInsuranceUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientInsurance.PatientInsuranceResponseVM;
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
public class PatientInsuranceController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientInsuranceController.class);

    private final PatientInsuranceService patientInsuranceService;

    public PatientInsuranceController(PatientInsuranceService patientInsuranceService) {
        this.patientInsuranceService = patientInsuranceService;
    }

    @PostMapping("/insurances")
    public ResponseEntity<PatientInsuranceResponseVM> createPatientInsurance(
            @Valid @RequestBody PatientInsuranceCreateDTO dto
    ) {
        LOG.debug("REST create PatientInsurance payload={}", dto);

        if (dto == null) {
            LOG.warn("[CREATE] PatientInsurance rejected: payload is null");
            throw new BadRequestAlertException("PatientInsurance payload is required", "patientInsurance", "payload.required");
        }

        PatientInsurance created = patientInsuranceService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/insurances/" + created.getId()))
                .body(PatientInsuranceResponseVM.ofEntity(created));
    }

    @PutMapping("/insurances/{id}")
    public ResponseEntity<PatientInsuranceResponseVM> updatePatientInsurance(
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

        if (dto.id() == null || !id.equals(dto.id())) {
            throw new BadRequestAlertException(
                    "Invalid id",
                    "patientInsurance",
                    "id.invalid"
            );
        }

        PatientInsurance existing = patientInsuranceService.findById(id);

        PatientInsurance updated = patientInsuranceService.update(existing, dto);

        return ResponseEntity.ok(PatientInsuranceResponseVM.ofEntity(updated));
    }



    @GetMapping("/insurances")
    public ResponseEntity<List<PatientInsuranceResponseVM>> getAllPatientInsurances(
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientInsurances pageable={}", pageable);

        Page<PatientInsurance> page = patientInsuranceService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<PatientInsuranceResponseVM> body = page.getContent()
                .stream()
                .map(PatientInsuranceResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/insurances/patient/{patientId}")
    public ResponseEntity<List<PatientInsuranceResponseVM>> getInsurancesByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list ALL PatientInsurances patientId={} pageable={}", patientId, pageable);

        if (patientId == null) {
            LOG.warn("[FIND_BY_PATIENT] PatientInsurance rejected: patientId is null");
            throw new BadRequestAlertException("Patient id is required", "patientInsurance", "patient.required");
        }
        Page<PatientInsurance> page = patientInsuranceService.getInsurancesByPatient(patientId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<PatientInsuranceResponseVM> body = page.getContent()
                .stream()
                .map(PatientInsuranceResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/insurances/{id}")
    public ResponseEntity<Void> deletePatientInsurance(
            @PathVariable Long id,
            @RequestParam(name = "deleteCoverages", defaultValue = "false") boolean deleteCoverages
    ) {
        LOG.debug("REST delete PatientInsurance id={} deleteCoverages={}", id, deleteCoverages);

        boolean deleted = patientInsuranceService.delete(id, deleteCoverages);
        if (deleted) return ResponseEntity.noContent().build();

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/insurances/{id}/coverages/count")
    public ResponseEntity<Long> countInsuranceCoverages(@PathVariable Long id) {
        LOG.debug("REST count coverages insuranceId={}", id);
        return ResponseEntity.ok(patientInsuranceService.countCoverages(id));
    }

}

package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientInsuranceCoverage;
import com.dazzle.asklepios.service.PatientInsuranceCoverageService;
import com.dazzle.asklepios.service.dto.patientInsuranceCoverage.PatientInsuranceCoverageCreateDTO;
import com.dazzle.asklepios.service.dto.patientInsuranceCoverage.PatientInsuranceCoverageUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.insuranceCoverage.PatientInsuranceCoverageResponseVM;
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
public class PatientInsuranceCoverageController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientInsuranceCoverageController.class);

    private final PatientInsuranceCoverageService coverageService;

    public PatientInsuranceCoverageController(PatientInsuranceCoverageService coverageService) {
        this.coverageService = coverageService;
    }

    @PostMapping("/insurance-coverages")
    public ResponseEntity<PatientInsuranceCoverageResponseVM> createCoverage(
            @Valid @RequestBody PatientInsuranceCoverageCreateDTO dto
    ) {
        LOG.debug("REST create PatientInsuranceCoverage payload={}", dto);
        if (dto == null) {
            throw new BadRequestAlertException("Payload is required", "patientInsuranceCoverage", "payload.required");
        }
        PatientInsuranceCoverage created = coverageService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/insurance-coverages/" + created.getId()))
                .body(PatientInsuranceCoverageResponseVM.ofEntity(created));
    }

    @PutMapping("/insurance-coverages/{id}")
    public ResponseEntity<PatientInsuranceCoverageResponseVM> updateCoverage(
            @PathVariable Long id,
            @Valid @RequestBody PatientInsuranceCoverageUpdateDTO dto
    ) {
        LOG.debug("REST update PatientInsuranceCoverage id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "PatientInsuranceCoverage payload is required",
                    "patientInsuranceCoverage",
                    "payload.required"
            );
        }

        if (dto.id() == null || !id.equals(dto.id())) {
            throw new BadRequestAlertException(
                    "Invalid id",
                    "patientInsuranceCoverage",
                    "id.invalid"
            );
        }

        PatientInsuranceCoverage existing = coverageService.findByIdOrThrow(id);

        PatientInsuranceCoverage updated = coverageService.update(existing, dto);

        return ResponseEntity.ok(PatientInsuranceCoverageResponseVM.ofEntity(updated));
    }

    @GetMapping("/insurance-coverages/insurance/{insuranceId}")
    public ResponseEntity<List<PatientInsuranceCoverageResponseVM>> getCoveragesByInsurance(
            @PathVariable Long insuranceId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientInsuranceCoverages insuranceId={} pageable={}", insuranceId, pageable);
        if (insuranceId == null) {
            throw new BadRequestAlertException("Insurance id is required", "patientInsuranceCoverage", "insurance.required");
        }
        Page<PatientInsuranceCoverage> page = coverageService.findAllByInsurance(insuranceId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<PatientInsuranceCoverageResponseVM> body = page.getContent()
                .stream()
                .map(PatientInsuranceCoverageResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/insurance-coverages/{id}")
    public ResponseEntity<Void> deleteCoverage(@PathVariable Long id) {
        LOG.debug("REST delete PatientInsuranceCoverage id={}", id);

        boolean deleted = coverageService.delete(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

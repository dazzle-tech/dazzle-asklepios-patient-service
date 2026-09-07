package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DentalProcedure;
import com.dazzle.asklepios.service.DentalProcedureService;
import com.dazzle.asklepios.service.dto.dentalProcedure.DentalProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.dentalProcedure.DentalProcedureUpdateDTO;
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

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DentalProcedureController {

    private static final Logger LOG = LoggerFactory.getLogger(DentalProcedureController.class);

    private final DentalProcedureService dentalProcedureService;

    public DentalProcedureController(DentalProcedureService dentalProcedureService) {
        this.dentalProcedureService = dentalProcedureService;
    }

    @PostMapping("/dental-procedures")
    public ResponseEntity<DentalProcedure> create(
            @Valid @RequestBody DentalProcedureCreateDTO dto
    ) {
        LOG.debug("REST create DentalProcedure payload={}", dto);
        DentalProcedure saved = dentalProcedureService.create(dto);
        return ResponseEntity
                .created(java.net.URI.create("/api/patient/dental-procedures/" + saved.getId()))
                .body(saved);
    }

    @GetMapping("/dental-procedures/by-patient/{patientId}")
    public ResponseEntity<List<DentalProcedure>> getAllByPatient(
            @PathVariable Long patientId,
            @RequestParam(name = "showCancelled", defaultValue = "false") boolean showCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST get all DentalProcedures by patientId={}", patientId);
        Page<DentalProcedure> page =
                dentalProcedureService.findAllByPatientId(patientId, showCancelled, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @PutMapping("/dental-procedures/{id}")
    public ResponseEntity<DentalProcedure> update(
            @PathVariable Long id,
            @Valid @RequestBody DentalProcedureUpdateDTO dto
    ) {
        LOG.debug("REST update DentalProcedure id={} payload={}", id, dto);
        return ResponseEntity.ok(dentalProcedureService.update(dto));
    }

    @PutMapping("/dental-procedures/{id}/cancel")
    public ResponseEntity<DentalProcedure> cancel(
            @PathVariable Long id, @RequestBody(required = false) String cancellationReason
    ) {
        LOG.debug("REST cancel DentalProcedure id={}", id);
        return ResponseEntity.ok(dentalProcedureService.cancel(id, cancellationReason));
    }
}
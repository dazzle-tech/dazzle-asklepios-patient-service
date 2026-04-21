package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DentalProcedureService;
import com.dazzle.asklepios.service.dto.dentalProcedure.DentalProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.dentalProcedure.DentalProcedureUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.DentalProcedure.DentalProcedureResponseVM;
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

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DentalProcedureController {

    private static final Logger LOG = LoggerFactory.getLogger(DentalProcedureController.class);

    private final DentalProcedureService dentalProcedureService;

    public DentalProcedureController(DentalProcedureService dentalProcedureService) {
        this.dentalProcedureService = dentalProcedureService;
    }

    /**
     * POST /dental-procedures: Create a new DentalProcedure
     */
    @PostMapping("/dental-procedures")
    public ResponseEntity<DentalProcedureResponseVM> create(
            @Valid @RequestBody DentalProcedureCreateDTO dto
    ) {
        LOG.debug("REST create DentalProcedure payload={}", dto);
        DentalProcedureResponseVM body = dentalProcedureService.create(dto);
        return ResponseEntity
                .created(java.net.URI.create("/api/patient/dental-procedures/" + body.id()))
                .body(body);
    }

    /**
     * GET /dental-procedures/by-patient/{patientId} : Get all dental procedures by patient
     */
    @GetMapping("/dental-procedures/by-patient/{patientId}")
    public ResponseEntity<List<DentalProcedureResponseVM>> getAllByPatient(
            @PathVariable Long patientId,
            @RequestParam(name = "showCancelled", defaultValue = "false") boolean showCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST get all DentalProcedures by patientId={}", patientId);
        Page<DentalProcedureResponseVM> page =
                dentalProcedureService.findAllByPatientId(patientId, showCancelled, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * PUT /dental-procedures/{id} : Update a DentalProcedure
     */
    @PutMapping("/dental-procedures/{id}")
    public ResponseEntity<DentalProcedureResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DentalProcedureUpdateDTO dto
    ) {
        LOG.debug("REST update DentalProcedure id={} payload={}", id, dto);
        return ResponseEntity.ok(dentalProcedureService.update(dto));
    }

    /**
     * PUT /dental-procedures/{id}/cancel : Cancel a DentalProcedure
     */
    @PutMapping("/dental-procedures/{id}/cancel")
    public ResponseEntity<DentalProcedureResponseVM> cancel(
            @PathVariable Long id
    ) {
        LOG.debug("REST cancel DentalProcedure id={}", id);
        return ResponseEntity.ok(dentalProcedureService.cancel(id));
    }
}
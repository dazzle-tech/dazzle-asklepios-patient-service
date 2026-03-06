package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.service.PatientProcedureService;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureCancelDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureUpdateDTO;
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
public class PatientProcedureController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientProcedureController.class);

    private final PatientProcedureService service;

    public PatientProcedureController(PatientProcedureService service) {
        this.service = service;
    }

    @PostMapping("/procedure")
    public ResponseEntity<PatientProcedure> create(
            @Valid @RequestBody PatientProcedureCreateDTO procedureCreateDTO
    ) {
        LOG.info("REST CREATE PatientProcedure payload={}", procedureCreateDTO);
        PatientProcedure created = service.create(procedureCreateDTO);
        LOG.info("REST CREATE PatientProcedure success id={}", created.getId());
        return ResponseEntity
                .created(URI.create("/api/patient/procedure/" + created.getId()))
                .body(created);
    }

    @PutMapping("/procedure/{id}")
    public ResponseEntity<PatientProcedure> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureUpdateDTO procedureUpdateDTO
    ) {
        LOG.info("REST UPDATE PatientProcedure id={} payload={}", id, procedureUpdateDTO);

        if (!id.equals(procedureUpdateDTO.id())) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id", "procedure", "id.mismatch");
        }

        PatientProcedure existing = service.findById(id);

        if (existing.getStatus() == ProcStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled procedure cannot be updated", "procedure", "already.cancelled");
        }

        if (procedureUpdateDTO.procedureId() == null &&
                procedureUpdateDTO.procedureLevel() == null &&
                procedureUpdateDTO.priority() == null &&
                procedureUpdateDTO.bodyPart() == null &&
                procedureUpdateDTO.side() == null &&
                procedureUpdateDTO.indicationId() == null &&
                procedureUpdateDTO.toFacilityId() == null &&
                procedureUpdateDTO.toDepartmentId() == null &&
                procedureUpdateDTO.scheduledDateTime() == null &&
                procedureUpdateDTO.notes() == null &&
                procedureUpdateDTO.extraDocumentation() == null
        ) {
            throw new BadRequestAlertException(
                    "No updatable fields provided", "procedure", "no.fields");
        }

        PatientProcedure updated = service.update(id, procedureUpdateDTO);
        LOG.info("REST UPDATE PatientProcedure success id={}", updated.getId());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/procedure/{id}/cancel")
    public ResponseEntity<PatientProcedure> cancel(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureCancelDTO procedureCancelDTO
    ) {
        LOG.info("REST CANCEL PatientProcedure id={} reason={}", id, procedureCancelDTO.cancellationReason());

        PatientProcedure cancelled = service.cancel(id, procedureCancelDTO.cancellationReason());
        LOG.info("REST CANCEL PatientProcedure success id={}", cancelled.getId());
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/procedure/by-encounter/{encounterId}")
    public ResponseEntity<List<PatientProcedure>> findByEncounter(
            @PathVariable Long encounterId,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.info("REST FIND PatientProcedure by encounterId={} includeCancelled={} pageable={}",
                encounterId, includeCancelled, pageable);

        Page<PatientProcedure> page = service.findByEncounter(encounterId, includeCancelled, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/procedure/by-patient/{patientId}")
    public ResponseEntity<List<PatientProcedure>> findByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.info("REST FIND PatientProcedure by patientId={} includeCancelled={} pageable={}",
                patientId, includeCancelled, pageable);

        Page<PatientProcedure> page = service.findByPatient(patientId, includeCancelled, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}
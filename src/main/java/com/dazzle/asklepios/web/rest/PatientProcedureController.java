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
@RequestMapping("/api/patient/procedure")
public class PatientProcedureController {

    private final PatientProcedureService service;

    public PatientProcedureController(PatientProcedureService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PatientProcedure> create(
            @Valid @RequestBody PatientProcedureCreateDTO dto
    ) {
        PatientProcedure created = service.create(dto);
        return ResponseEntity
                .created(URI.create("/api/patient/procedure/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientProcedure> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureUpdateDTO dto
    ) {
        if (!id.equals(dto.id())) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "procedure",
                    "id.mismatch"
            );
        }

        PatientProcedure existing = service.findById(id);

        if (existing.getStatus() == ProcStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled procedure cannot be updated",
                    "procedure",
                    "already.cancelled"
            );
        }

        if (dto.procedureId() == null &&
                dto.procedureLevel() == null &&
                dto.priority() == null &&
                dto.bodyPart() == null &&
                dto.side() == null &&
                dto.indicationId() == null &&
                dto.toFacilityId() == null &&
                dto.toDepartmentId() == null &&
                dto.scheduledDateTime() == null &&
                dto.notes() == null &&
                dto.extraDocumentation() == null
        ) {
            throw new BadRequestAlertException(
                    "No updatable fields provided",
                    "procedure",
                    "no.fields"
            );
        }

        return ResponseEntity.ok(service.update(id, dto));
    }



    @PutMapping("/{id}/cancel")
    public ResponseEntity<PatientProcedure> cancel(
            @PathVariable Long id,
            @Valid @RequestBody PatientProcedureCancelDTO dto
    ) {
        return ResponseEntity.ok(
                service.cancel(id, dto.cancellationReason(), dto.cancelledBy())
        );
    }

    @GetMapping("/by-encounter/{encounterId}")
    public ResponseEntity<List<PatientProcedure>> findByEncounter(
            @PathVariable Long encounterId,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @ParameterObject Pageable pageable
    ) {
        Page<PatientProcedure> page =
                service.findByEncounter(encounterId, includeCancelled, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(), page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}

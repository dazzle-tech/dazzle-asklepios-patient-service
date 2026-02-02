package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Consultation;
import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.domain.enumeration.DestinationType;
import com.dazzle.asklepios.service.ConsultationService;
import com.dazzle.asklepios.service.dto.consultation.ConsultationCancelDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationCreateDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationUpdateDTO;
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
import org.springframework.validation.annotation.Validated;
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
import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/patient/consultation")
public class ConsultationController {

    private static final Logger LOG =
            LoggerFactory.getLogger(ConsultationController.class);

    private final ConsultationService service;

    public ConsultationController(ConsultationService service) {
        this.service = service;
    }


    @PostMapping
    public ResponseEntity<Consultation> create(
            @Valid @RequestBody ConsultationCreateDTO dto
    ) {
        LOG.debug("REST create Consultation payload={}", dto);

        if (dto == null || dto.patientId() == null) {
            LOG.warn("REST create Consultation - Patient id is missing");
            throw new BadRequestAlertException(
                    "Patient id is required",
                    "consultation",
                    "patient.required"
            );
        }

        DestinationType destinationType;
        try {
            destinationType = DestinationType.valueOf(dto.destinationType().trim().toUpperCase());
            LOG.debug("REST create Consultation - Parsed destinationType={}", destinationType);
        } catch (Exception ex) {
            LOG.error("REST create Consultation - Invalid destinationType={}", dto.destinationType(), ex);
            throw new BadRequestAlertException(
                    "Invalid destinationType",
                    "consultation",
                    "destinationType.invalid"
            );
        }

        if (destinationType == DestinationType.DEPARTMENT) {
            if (dto.toDepartmentId() == null) {
                LOG.warn("REST create Consultation - toDepartmentId is missing for DEPARTMENT destination");
                throw new BadRequestAlertException(
                        "toDepartmentId is required when destinationType = DEPARTMENT",
                        "consultation",
                        "department.required"
                );
            }
        }

        if (destinationType == DestinationType.CONSULTANT) {
            if (dto.consultantSpeciality() == null
                    || dto.consultantSpeciality().isBlank()
                    || dto.practitionerId() == null) {

                LOG.warn("REST create Consultation - Missing consultant details for CONSULTANT destination");
                throw new BadRequestAlertException(
                        "consultantSpeciality and practitionerId are required when destinationType = CONSULTANT",
                        "consultation",
                        "consultant.required"
                );
            }
        }

        Consultation created = service.create(dto);
        LOG.info("REST create Consultation - Successfully created consultation id={}", created.getId());

        return ResponseEntity
                .created(URI.create("/api/patient/consultation/" + created.getId()))
                .body(created);
    }



    @PutMapping("/{id}")
    public ResponseEntity<Consultation> update(
            @PathVariable Long id,
            @Valid @RequestBody ConsultationUpdateDTO dto
    ) {
        LOG.debug("REST update Consultation id={} payload={}", id, dto);

        if (dto == null || dto.id() == null || !dto.id().equals(id)) {
            LOG.warn("REST update Consultation - Path id={} does not match payload id={}", id, dto != null ? dto.id() : null);
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "consultation",
                    "id.mismatch"
            );
        }

        Consultation existing = service.findById(id);

        if (existing.getStatus() == ConsultationStatus.CANCELLED) {
            LOG.warn("REST update Consultation - Attempt to update cancelled consultation id={}", id);
            throw new BadRequestAlertException(
                    "Cancelled consultation cannot be updated",
                    "consultation",
                    "already.cancelled"
            );
        }

        Consultation updated = service.update(id, dto);
        LOG.info("REST update Consultation - Successfully updated consultation id={}", id);
        return ResponseEntity.ok(updated);
    }



    @PutMapping("/{id}/cancel")
    public ResponseEntity<Consultation> cancel(
            @PathVariable Long id,
            @Valid @RequestBody ConsultationCancelDTO dto
    ) {
        LOG.debug("REST cancel Consultation id={} reason={} cancelledBy={}", id, dto.cancellationReason(), dto.cancelledBy());

        Consultation existing = service.findById(id);

        if (existing.getStatus() == ConsultationStatus.CANCELLED) {
            LOG.warn("REST cancel Consultation - Consultation already cancelled id={}", id);
            throw new BadRequestAlertException(
                    "Consultation already cancelled",
                    "consultation",
                    "already.cancelled"
            );
        }

        if (existing.getStatus() == ConsultationStatus.COMPLETED) {
            LOG.warn("REST cancel Consultation - Attempt to cancel completed consultation id={}", id);
            throw new BadRequestAlertException(
                    "Completed consultation cannot be cancelled",
                    "consultation",
                    "already.completed"
            );
        }

        Consultation cancelled =
                service.cancel(id, dto.cancellationReason(), dto.cancelledBy());

        LOG.info("REST cancel Consultation - Successfully cancelled consultation id={}", id);
        return ResponseEntity.ok(cancelled);
    }



    @GetMapping("/not-cancelled/by-encounter/{encounterId}")
    public ResponseEntity<List<Consultation>> findNotCancelledByEncounter(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST find NOT_CANCELLED consultations encounterId={} pageable={}",
                encounterId, pageable
        );

        Page<Consultation> page =
                service.findNotCancelled(encounterId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(), page
                );

        LOG.debug("REST find NOT_CANCELLED consultations - Returning {} results", page.getContent().size());
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/by-encounter/{encounterId}")
    public ResponseEntity<List<Consultation>> findByEncounter(
            @PathVariable Long encounterId,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST find consultations encounterId={} fromDate={} toDate={} includeCancelled={} pageable={}",
                encounterId, fromDate, toDate, includeCancelled, pageable
        );

        Page<Consultation> page;

        boolean hasFrom = fromDate != null;
        boolean hasTo = toDate != null;

        if (hasFrom && hasTo && includeCancelled) {
            LOG.debug("REST find consultations - Using date range WITH cancelled");
            page = service.findByEncounterWithDateRange(
                    encounterId, fromDate, toDate, pageable
            );
        } else if (hasFrom && hasTo) {
            LOG.debug("REST find consultations - Using date range WITHOUT cancelled");
            page = service.findByEncounterWithDateRangeNotCancelled(
                    encounterId, fromDate, toDate, pageable
            );
        } else if (hasFrom && includeCancelled) {
            LOG.debug("REST find consultations - Using from date WITH cancelled");
            page = service.findByEncounterFromDate(
                    encounterId, fromDate, pageable
            );
        } else if (hasFrom) {
            LOG.debug("REST find consultations - Using from date WITHOUT cancelled");
            page = service.findByEncounterFromDateNotCancelled(
                    encounterId, fromDate, pageable
            );
        } else if (hasTo && includeCancelled) {
            LOG.debug("REST find consultations - Using to date WITH cancelled");
            page = service.findByEncounterToDate(
                    encounterId, toDate, pageable
            );
        } else if (hasTo) {
            LOG.debug("REST find consultations - Using to date WITHOUT cancelled");
            page = service.findByEncounterToDateNotCancelled(
                    encounterId, toDate, pageable
            );
        } else if (includeCancelled) {
            LOG.debug("REST find consultations - Using all WITH cancelled");
            page = service.findByEncounter(encounterId, pageable);
        } else {
            LOG.debug("REST find consultations - Using all WITHOUT cancelled");
            page = service.findByEncounterNotCancelled(encounterId, pageable);
        }

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(), page
                );

        LOG.debug("REST find consultations - Returning {} results", page.getContent().size());
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/destination/department-ids/{encounterId}")
    public ResponseEntity<List<Long>> getDepartmentIds(
            @PathVariable Long encounterId
    ) {
        LOG.debug("REST get departmentIds encounterId={}", encounterId);

        if (encounterId == null) {
            LOG.warn("REST get departmentIds - Encounter id is null");
            throw new BadRequestAlertException(
                    "Encounter id is required",
                    "consultation",
                    "encounter.required"
            );
        }

        List<Long> departmentIds = service.getDepartmentIdsByEncounterId(encounterId);
        LOG.debug("REST get departmentIds - Returning {} department IDs", departmentIds.size());

        return ResponseEntity.ok(departmentIds);
    }

    @GetMapping("/destination/practitioner-ids/{encounterId}")
    public ResponseEntity<List<Long>> getPractitionerIds(
            @PathVariable Long encounterId
    ) {
        LOG.debug("REST get practitionerIds encounterId={}", encounterId);

        if (encounterId == null) {
            LOG.warn("REST get practitionerIds - Encounter id is null");
            throw new BadRequestAlertException(
                    "Encounter id is required",
                    "consultation",
                    "encounter.required"
            );
        }

        List<Long> practitionerIds = service.getPractitionerIdsByEncounterId(encounterId);
        LOG.debug("REST get practitionerIds - Returning {} practitioner IDs", practitionerIds.size());

        return ResponseEntity.ok(practitionerIds);
    }

}
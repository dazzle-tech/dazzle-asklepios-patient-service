package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.EncounterVaccination;
import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import com.dazzle.asklepios.service.EncounterVaccinationService;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationCancelDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationCreateDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationReviewDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationUpdateDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.PatientVaccineDetailsDTO;
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
public class EncounterVaccinationController {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterVaccinationController.class);

    private final EncounterVaccinationService encounterVaccinationService;

    public EncounterVaccinationController(EncounterVaccinationService encounterVaccinationService) {
        this.encounterVaccinationService = encounterVaccinationService;
    }

    @PostMapping("/encounter-vaccination")
    public ResponseEntity<EncounterVaccination> create(
            @Valid @RequestBody EncounterVaccinationCreateDTO createRequest
    ) {
        LOG.debug("REST create EncounterVaccination payload={}", createRequest);

        if (createRequest == null) {
            LOG.warn("[CREATE] EncounterVaccination rejected: payload is null");
            throw new BadRequestAlertException(
                    "EncounterVaccination payload is required",
                    "encounterVaccination",
                    "payload.required"
            );
        }

        if (createRequest.patientId() == null) {
            LOG.warn("[CREATE] EncounterVaccination rejected: patientId is null payload={}", createRequest);
            throw new BadRequestAlertException(
                    "Patient id is required",
                    "encounterVaccination",
                    "patient.required"
            );
        }

        if (Boolean.FALSE.equals(createRequest.isExternalFacility()) && createRequest.externalFacilityName() != null) {
            LOG.debug("[CREATE] Clearing externalFacilityName because isExternalFacility=false");
            createRequest = new EncounterVaccinationCreateDTO(
                    createRequest.patientId(),
                    createRequest.encounterId(),
                    createRequest.vaccineId(),
                    createRequest.vaccineBrandId(),
                    createRequest.vaccineDoseId(),
                    createRequest.vaccineLotNumber(),
                    createRequest.dateAdministered(),
                    createRequest.status(),
                    createRequest.cancellationReason(),
                    createRequest.administeredLocation(),
                    createRequest.administrationReactions(),
                    createRequest.isExternalFacility(),
                    null,
                    createRequest.notes()
            );
        }

        EncounterVaccination createdEncounterVaccination = encounterVaccinationService.create(createRequest);

        return ResponseEntity
                .created(URI.create("/api/patient/encounter-vaccination/" + createdEncounterVaccination.getId()))
                .body(createdEncounterVaccination);
    }

    @PutMapping("/encounter-vaccination/{id}")
    public ResponseEntity<EncounterVaccination> update(
            @PathVariable Long id,
            @Valid @RequestBody EncounterVaccinationUpdateDTO updateRequest
    ) {
        LOG.debug("REST update EncounterVaccination id={} payload={}", id, updateRequest);

        if (updateRequest == null) {
            LOG.warn("[UPDATE] EncounterVaccination rejected: payload is null");
            throw new BadRequestAlertException(
                    "EncounterVaccination payload is required",
                    "encounterVaccination",
                    "payload.required"
            );
        }

        if (updateRequest.patientId() == null) {
            LOG.warn("[UPDATE] EncounterVaccination rejected: patientId is null payload={}", updateRequest);
            throw new BadRequestAlertException(
                    "Patient id is required",
                    "encounterVaccination",
                    "patient.required"
            );
        }

        // clear name rule at API level (optional redundancy; DB + DTO validation already cover it)
        if (Boolean.FALSE.equals(updateRequest.isExternalFacility()) && updateRequest.externalFacilityName() != null) {
            LOG.debug("[UPDATE] Clearing externalFacilityName because isExternalFacility=false");
            updateRequest = new EncounterVaccinationUpdateDTO(
                    updateRequest.id(),
                    updateRequest.patientId(),
                    updateRequest.encounterId(),
                    updateRequest.vaccineId(),
                    updateRequest.vaccineBrandId(),
                    updateRequest.vaccineDoseId(),
                    updateRequest.vaccineLotNumber(),
                    updateRequest.dateAdministered(),
                    updateRequest.status(),
                    updateRequest.administeredLocation(),
                    updateRequest.administrationReactions(),
                    updateRequest.isExternalFacility(),
                    null,
                    updateRequest.notes()
            );
        }

        EncounterVaccination updatedEncounterVaccination = encounterVaccinationService.update(id, updateRequest);
        return ResponseEntity.ok(updatedEncounterVaccination);
    }

    @PutMapping("/encounter-vaccination/cancel")
    public ResponseEntity<EncounterVaccination> cancel(
            @Valid @RequestBody EncounterVaccinationCancelDTO cancelRequest
    ) {
        LOG.debug(
                "REST cancel EncounterVaccination id={} reason={}",
                cancelRequest.id(), cancelRequest.cancellationReason()
        );

        if (cancelRequest.id() == null) {
            LOG.warn("[CANCEL] EncounterVaccination rejected: id is null");
            throw new BadRequestAlertException("EncounterVaccination id is required", "encounterVaccination", "id.required");
        }


        EncounterVaccination existing = encounterVaccinationService.getById(cancelRequest.id());

        if (existing.getStatus() == EncounterVaccinationStatus.CANCELLED) {
            throw new BadRequestAlertException("Encounter vaccination already cancelled", "encounterVaccination", "already.cancelled");
        }
        if (existing.getStatus() == EncounterVaccinationStatus.REVIEW) {
            throw new BadRequestAlertException("Reviewed encounter vaccination cannot be cancelled", "encounterVaccination", "already.reviewed");
        }
        if (existing.getStatus() == EncounterVaccinationStatus.RESOLVED) {
            throw new BadRequestAlertException("Resolved encounter vaccination cannot be cancelled", "encounterVaccination", "already.resolved");
        }

        EncounterVaccination cancelledEncounterVaccination = encounterVaccinationService.cancel(cancelRequest);

        return ResponseEntity.ok(cancelledEncounterVaccination);
    }

    @PutMapping("/review")
    public ResponseEntity<EncounterVaccination> review(
            @Valid @RequestBody EncounterVaccinationReviewDTO reviewRequest
    ) {
        LOG.debug(
                "REST review EncounterVaccination id={}",
                reviewRequest.id()
        );

        if (reviewRequest.id() == null) {
            LOG.warn("[REVIEW] EncounterVaccination rejected: id is null");
            throw new BadRequestAlertException("EncounterVaccination id is required", "encounterVaccination", "id.required");
        }


        EncounterVaccination existing = encounterVaccinationService.getById(reviewRequest.id());

        if (existing.getStatus() == EncounterVaccinationStatus.REVIEW) {
            throw new BadRequestAlertException("Encounter vaccination already reviewed", "encounterVaccination", "already.reviewed");
        }
        if (existing.getStatus() == EncounterVaccinationStatus.CANCELLED) {
            throw new BadRequestAlertException("Cancelled encounter vaccination cannot be reviewed", "encounterVaccination", "already.cancelled");
        }
        if (existing.getStatus() == EncounterVaccinationStatus.RESOLVED) {
            throw new BadRequestAlertException("Resolved encounter vaccination cannot be reviewed", "encounterVaccination", "already.resolved");
        }

        EncounterVaccination reviewedEncounterVaccination = encounterVaccinationService.review(reviewRequest);

        return ResponseEntity.ok(reviewedEncounterVaccination);
    }

    @GetMapping("/encounter-vaccination/encounter/{encounterId}")
    public ResponseEntity<List<EncounterVaccination>> listEncounterVaccinationsActive(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list EncounterVaccinations (ACTIVE) encounterId={} pageable={}", encounterId, pageable);

        if (encounterId == null) {
            LOG.warn("[FIND_ACTIVE_BY_ENCOUNTER] rejected: encounterId is null");
            throw new BadRequestAlertException("Encounter id is required", "encounterVaccination", "encounter.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findEncounterVaccinationsActive(encounterId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/encounter-vaccination/encounter/{encounterId}/all")
    public ResponseEntity<List<EncounterVaccination>> listEncounterVaccinationsAll(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list EncounterVaccinations (ALL) encounterId={} pageable={}", encounterId, pageable);

        if (encounterId == null) {
            LOG.warn("[FIND_ALL_BY_ENCOUNTER] rejected: encounterId is null");
            throw new BadRequestAlertException("Encounter id is required", "encounterVaccination", "encounter.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findEncounterVaccinationsAll(encounterId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/encounter-vaccination/patient/{patientId}")
    public ResponseEntity<List<EncounterVaccination>> listPatientVaccinationsActive(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patient EncounterVaccinations (ACTIVE) patientId={} pageable={}", patientId, pageable);

        if (patientId == null) {
            LOG.warn("[FIND_ACTIVE_BY_PATIENT] rejected: patientId is null");
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findPatientVaccinationsActive(patientId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/encounter-vaccination/patient/{patientId}/all")
    public ResponseEntity<List<EncounterVaccination>> listPatientVaccinationsAll(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patient EncounterVaccinations (ALL) patientId={} pageable={}", patientId, pageable);

        if (patientId == null) {
            LOG.warn("[FIND_ALL_BY_PATIENT] rejected: patientId is null");
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findPatientVaccinationsAll(patientId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/encounter-vaccination/patient/{patientId}/vaccine-ids")
    public ResponseEntity<List<Long>> listPatientVaccineIds(@PathVariable Long patientId) {
        LOG.debug("REST list Patient vaccineIds patientId={}", patientId);

        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }

        List<Long> vaccineIds = encounterVaccinationService.findPatientVaccineIds(patientId);
        return ResponseEntity.ok(vaccineIds);
    }

    @GetMapping("/encounter-vaccination/patient/{patientId}/vaccine/{vaccineId}/details")
    public ResponseEntity<PatientVaccineDetailsDTO> getPatientVaccineDetails(
            @PathVariable Long patientId,
            @PathVariable Long vaccineId,
            @RequestParam(name = "includeCancelled", defaultValue = "false") boolean includeCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST get Patient vaccine details patientId={} vaccineId={} includeCancelled={} pageable={}",
                patientId, vaccineId, includeCancelled, pageable);

        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }
        if (vaccineId == null) {
            throw new BadRequestAlertException("Vaccine id is required", "encounterVaccination", "vaccine.required");
        }

        PatientVaccineDetailsDTO detailsResponse =
                encounterVaccinationService.findPatientVaccineDetails(patientId, vaccineId, includeCancelled, pageable);

        return ResponseEntity.ok(detailsResponse);
    }
}
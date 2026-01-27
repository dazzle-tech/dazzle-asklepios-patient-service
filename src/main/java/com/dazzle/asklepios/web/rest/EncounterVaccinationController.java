package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.EncounterVaccination;
import com.dazzle.asklepios.service.EncounterVaccinationService;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationCreateDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationUpdateDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.PatientVaccineDetailsDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.EncounterVaccinationResponseVM;
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
@RequestMapping("/api/patient/encounter-vaccination")
public class EncounterVaccinationController {

    private static final Logger LOG =
            LoggerFactory.getLogger(EncounterVaccinationController.class);

    private final EncounterVaccinationService encounterVaccinationService;

    public EncounterVaccinationController(
            EncounterVaccinationService encounterVaccinationService
    ) {
        this.encounterVaccinationService = encounterVaccinationService;
    }

    @PostMapping
    public ResponseEntity<EncounterVaccinationResponseVM> create(
            @Valid @RequestBody EncounterVaccinationCreateDTO dto
    ) {
        LOG.debug("REST create EncounterVaccination payload={}", dto);
        if (dto == null) {
            LOG.warn("[CREATE] EncounterVaccination rejected: payload is null");
            throw new BadRequestAlertException("EncounterVaccination payload is required", "encounterVaccination", "payload.required");
        }

        if (dto.patientId() == null) {
            LOG.warn("[CREATE] EncounterVaccination rejected: patientId is null payload={}", dto);
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }

        if (dto.dateAdministered() == null) {
            LOG.warn("[CREATE] EncounterVaccination rejected: dateAdministered is null payload={}", dto);
            throw new BadRequestAlertException("dateAdministered is required", "encounterVaccination", "dateAdministered.required");
        }
        EncounterVaccination created =
                encounterVaccinationService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/encounter-vaccination/" + created.getId()))
                .body(EncounterVaccinationResponseVM.ofEntity(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EncounterVaccinationResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody EncounterVaccinationUpdateDTO dto
    ) {
        LOG.debug("REST update EncounterVaccination id={} payload={}", id, dto);
        if (dto == null) {
            LOG.warn("[UPDATE] EncounterVaccination rejected: payload is null");
            throw new BadRequestAlertException("EncounterVaccination payload is required", "encounterVaccination", "payload.required");
        }

        if (dto.patientId() == null) {
            LOG.warn("[UPDATE] EncounterVaccination rejected: patientId is null payload={}", dto);
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }

        if (dto.dateAdministered() == null) {
            LOG.warn("[UPDATE] EncounterVaccination rejected: dateAdministered is null payload={}", dto);
            throw new BadRequestAlertException("dateAdministered is required", "encounterVaccination", "dateAdministered.required");
        }

        if (dto.id() == null || !dto.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "encounterVaccination",
                    "id.mismatch"
            );
        }

        return encounterVaccinationService.update(id, dto)
                .map(EncounterVaccinationResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<EncounterVaccinationResponseVM> cancel(
            @PathVariable Long id,
            @RequestParam Long canceledBy,
            @RequestParam String reason
    ) {
        LOG.debug("REST cancel EncounterVaccination id={} reason={}", id, reason);

        if (reason == null || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required",
                    "encounterVaccination",
                    "reason.required"
            );
        }
        if (id == null) {
            LOG.warn("[CANCEL] EncounterVaccination rejected: id is null");
            throw new BadRequestAlertException("EncounterVaccination id is required", "encounterVaccination", "id.required");
        }
        EncounterVaccination cancelled =
                encounterVaccinationService.cancel(id, reason , canceledBy);

        return ResponseEntity.ok(
                EncounterVaccinationResponseVM.ofEntity(cancelled)
        );
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<EncounterVaccinationResponseVM> review(
            @PathVariable Long id,
            @RequestParam Long reviewedBy
    ) {
        LOG.debug("REST review EncounterVaccination id={} reviewedBy={}", id, reviewedBy);
        if (id == null) {
            LOG.warn("[REVIEW] EncounterVaccination rejected: id is null");
            throw new BadRequestAlertException("EncounterVaccination id is required", "encounterVaccination", "id.required");
        }
        if (reviewedBy == null) {
            throw new BadRequestAlertException(
                    "ReviewedBy is required",
                    "encounterVaccination",
                    "reviewedBy.required"
            );
        }

        EncounterVaccination reviewed =
                encounterVaccinationService.review(id, reviewedBy);

        return ResponseEntity.ok(
                EncounterVaccinationResponseVM.ofEntity(reviewed)
        );
    }

    @GetMapping("/encounter/{encounterId}")
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

    @GetMapping("/encounter/{encounterId}/all")
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

    @GetMapping("/encounter/{encounterId}/cancelled")
    public ResponseEntity<List<EncounterVaccinationResponseVM>> listEncounterVaccinationsCancelled(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list EncounterVaccinations (CANCELLED) encounterId={} pageable={}", encounterId, pageable);
        if (encounterId == null) {
            LOG.warn("[FIND_CANCELLED_BY_ENCOUNTER] rejected: encounterId is null");
            throw new BadRequestAlertException("Encounter id is required", "encounterVaccination", "encounter.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findEncounterVaccinationsCancelled(encounterId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        List<EncounterVaccinationResponseVM> body =
                page.getContent().stream()
                        .map(EncounterVaccinationResponseVM::ofEntity)
                        .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/patient/{patientId}")
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

    @GetMapping("/patient/{patientId}/all")
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

    @GetMapping("/patient/{patientId}/cancelled")
    public ResponseEntity<List<EncounterVaccinationResponseVM>> listPatientVaccinationsCancelled(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patient EncounterVaccinations (CANCELLED) patientId={} pageable={}", patientId, pageable);

        if (patientId == null) {
            LOG.warn("[FIND_CANCELLED_BY_PATIENT] rejected: patientId is null");
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findPatientVaccinationsCancelled(patientId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        List<EncounterVaccinationResponseVM> body =
                page.getContent().stream()
                        .map(EncounterVaccinationResponseVM::ofEntity)
                        .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }


    @GetMapping("/patient/{patientId}/encounter/{encounterId}")
    public ResponseEntity<List<EncounterVaccination>> listPatientEncounterVaccinationsActive(
            @PathVariable Long patientId,
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patient+Encounter EncounterVaccinations (ACTIVE) patientId={} encounterId={} pageable={}",
                patientId, encounterId, pageable);

        if (patientId == null) {
            LOG.warn("[FIND_ACTIVE_BY_PATIENT_ENCOUNTER] rejected: patientId is null");
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }
        if (encounterId == null) {
            LOG.warn("[FIND_ACTIVE_BY_PATIENT_ENCOUNTER] rejected: encounterId is null");
            throw new BadRequestAlertException("Encounter id is required", "encounterVaccination", "encounter.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findPatientEncounterVaccinationsActive(patientId, encounterId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/patient/{patientId}/encounter/{encounterId}/all")
    public ResponseEntity<List<EncounterVaccination>> listPatientEncounterVaccinationsAll(
            @PathVariable Long patientId,
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patient+Encounter EncounterVaccinations (ALL) patientId={} encounterId={} pageable={}",
                patientId, encounterId, pageable);

        if (patientId == null) {
            LOG.warn("[FIND_ALL_BY_PATIENT_ENCOUNTER] rejected: patientId is null");
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }
        if (encounterId == null) {
            LOG.warn("[FIND_ALL_BY_PATIENT_ENCOUNTER] rejected: encounterId is null");
            throw new BadRequestAlertException("Encounter id is required", "encounterVaccination", "encounter.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findPatientEncounterVaccinationsAll(patientId, encounterId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/patient/{patientId}/vaccine-ids")
    public ResponseEntity<List<Long>> listPatientVaccineIds(
            @PathVariable Long patientId
    ) {
        LOG.debug("REST list Patient vaccineIds patientId={}", patientId);

        List<Long> vaccineIds = encounterVaccinationService.findPatientVaccineIds(patientId);

        return ResponseEntity.ok(vaccineIds);
    }


    @GetMapping("/patient/{patientId}/encounter/{encounterId}/cancelled")
    public ResponseEntity<List<EncounterVaccinationResponseVM>> listPatientEncounterVaccinationsCancelled(
            @PathVariable Long patientId,
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patient+Encounter EncounterVaccinations (CANCELLED) patientId={} encounterId={} pageable={}",
                patientId, encounterId, pageable);

        if (patientId == null) {
            LOG.warn("[FIND_CANCELLED_BY_PATIENT_ENCOUNTER] rejected: patientId is null");
            throw new BadRequestAlertException("Patient id is required", "encounterVaccination", "patient.required");
        }
        if (encounterId == null) {
            LOG.warn("[FIND_CANCELLED_BY_PATIENT_ENCOUNTER] rejected: encounterId is null");
            throw new BadRequestAlertException("Encounter id is required", "encounterVaccination", "encounter.required");
        }

        Page<EncounterVaccination> page =
                encounterVaccinationService.findPatientEncounterVaccinationsCancelled(patientId, encounterId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        List<EncounterVaccinationResponseVM> body =
                page.getContent().stream()
                        .map(EncounterVaccinationResponseVM::ofEntity)
                        .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
    @GetMapping("/patient/{patientId}/vaccine/{vaccineId}/details")
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
        if (pageable == null) {
            throw new BadRequestAlertException("Pageable is required", "encounterVaccination", "pageable.required");
        }
        PatientVaccineDetailsDTO dto =
                encounterVaccinationService.findPatientVaccineDetails(patientId, vaccineId, includeCancelled, pageable);

        return ResponseEntity.ok(dto);
    }







}







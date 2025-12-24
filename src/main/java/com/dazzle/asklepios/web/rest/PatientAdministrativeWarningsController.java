package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import com.dazzle.asklepios.service.PatientAdministrativeWarningsService;
import com.dazzle.asklepios.service.dto.patientAdministrativeWarnings.PatientAdministrativeWarningsCreateDTO;
import com.dazzle.asklepios.service.dto.patientAdministrativeWarnings.PatientAdministrativeWarningsResolveDTO;
import com.dazzle.asklepios.service.dto.patientAdministrativeWarnings.PatientAdministrativeWarningsUndoResolveDTO;
import com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings.PatientAdministrativeWarningsResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientAdministrativeWarningsController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientAdministrativeWarningsController.class);

    private final PatientAdministrativeWarningsService patientAdministrativeWarningsService;

    public PatientAdministrativeWarningsController(PatientAdministrativeWarningsService patientAdministrativeWarningsService) {
        this.patientAdministrativeWarningsService = patientAdministrativeWarningsService;
    }

    /**
     * {@code POST /patient-administrative-warnings} : Create a new patient administrative warning.
     *
     * @param vm the creation payload.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and body of the created warning,
     *         with a {@code Location} header pointing to the new resource;
     *         or {@code 400 (Bad Request)} if payload is invalid or patient does not exist.
     */
    @PostMapping("/patient-administrative-warnings")
    public ResponseEntity<PatientAdministrativeWarningsResponseVM> create(
            @Valid @RequestBody PatientAdministrativeWarningsCreateDTO vm
    ) {
        LOG.debug("REST create PatientAdministrativeWarnings payload={}", vm);
        PatientAdministrativeWarnings saved = patientAdministrativeWarningsService.create(vm);
        PatientAdministrativeWarningsResponseVM body = PatientAdministrativeWarningsResponseVM.ofEntity(saved);

        return ResponseEntity
                .created(URI.create("/api/patient/patient-administrative-warnings/" + saved.getId()))
                .body(body);
    }

    /**
     * {@code PATCH /patient-administrative-warnings/{id}/resolve} :
     * Resolve a patient administrative warning (set resolved=true).
     *
     * @param id the identifier of the warning to resolve.
     * @param vm the resolve payload (must have matching id).
     * @return {@link ResponseEntity} with status {@code 200 (OK)} and updated warning,
     *         or {@code 400 (Bad Request)} if id mismatch,
     *         or {@code 404 (Not Found)} if warning does not exist.
     */
    @PatchMapping("/patient-administrative-warnings/{id}/resolve")
    public ResponseEntity<PatientAdministrativeWarningsResponseVM> resolve(
            @PathVariable Long id,
            @Valid @RequestBody PatientAdministrativeWarningsResolveDTO vm
    ) {
        LOG.debug("REST resolve PatientAdministrativeWarnings id={} payload={}", id, vm);
        if (vm.id() == null || !id.equals(vm.id())) {
            return ResponseEntity.badRequest().build();
        }
        PatientAdministrativeWarnings updated = patientAdministrativeWarningsService.resolve(vm);
        return ResponseEntity.ok(PatientAdministrativeWarningsResponseVM.ofEntity(updated));
    }

    /**
     * {@code PATCH /patient-administrative-warnings/{id}/undo-resolve} :
     * Undo resolve of a patient administrative warning (set resolved=false).
     *
     * @param id the identifier of the warning to undo resolve.
     * @param vm the undo resolve payload (must have matching id).
     * @return {@link ResponseEntity} with status {@code 200 (OK)} and updated warning,
     *         or {@code 400 (Bad Request)} if id mismatch,
     *         or {@code 404 (Not Found)} if warning does not exist.
     */
    @PatchMapping("/patient-administrative-warnings/{id}/undo-resolve")
    public ResponseEntity<PatientAdministrativeWarningsResponseVM> undoResolve(
            @PathVariable Long id,
            @Valid @RequestBody PatientAdministrativeWarningsUndoResolveDTO vm
    ) {
        LOG.debug("REST undoResolve PatientAdministrativeWarnings id={} payload={}", id, vm);
        if (vm.id() == null || !id.equals(vm.id())) {
            return ResponseEntity.badRequest().build();
        }
        PatientAdministrativeWarnings updated = patientAdministrativeWarningsService.undoResolve(vm);
        return ResponseEntity.ok(PatientAdministrativeWarningsResponseVM.ofEntity(updated));
    }

    /**
     * {@code GET /patient-administrative-warnings/patient/{patientId}} :
     * Get all warnings for a given patient (no pagination).
     *
     * @param patientId the patient identifier.
     * @return {@link ResponseEntity} with status {@code 200 (OK)} and list of warnings.
     */
    @GetMapping("/patient-administrative-warnings/patient/{patientId}")
    public ResponseEntity<List<PatientAdministrativeWarningsResponseVM>> getByPatientId(@PathVariable Long patientId) {
        LOG.debug("REST list PatientAdministrativeWarnings by patientId={}", patientId);
        List<PatientAdministrativeWarnings> list = patientAdministrativeWarningsService.getByPatientId(patientId);
        List<PatientAdministrativeWarningsResponseVM> body = list.stream()
                .map(PatientAdministrativeWarningsResponseVM::ofEntity)
                .toList();
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    /**
     * {@code GET /patient-administrative-warnings/patient/{patientId}/search} :
     * Get warnings for a patient filtered by warningType OR description (contains, ignore case).
     *
     * If {@code q} is null/blank, returns all warnings for the patient.
     *
     * @param patientId the patient identifier.
     * @param searchText         the search text to match against warningType or description.
     * @return {@link ResponseEntity} with status {@code 200 (OK)} and list of warnings.
     */
    @GetMapping("/patient-administrative-warnings/patient/{patientId}/search")
    public ResponseEntity<List<PatientAdministrativeWarningsResponseVM>> getByPatientIdAndQuery(
            @PathVariable Long patientId,
            @RequestParam(name = "searchText", required = false) String searchText
    ) {
        LOG.debug("REST list PatientAdministrativeWarnings by patientId={} q='{}'", patientId, searchText);
        List<PatientAdministrativeWarnings> list =
                patientAdministrativeWarningsService.searchByPatientId(patientId, searchText);

        List<PatientAdministrativeWarningsResponseVM> body = list.stream()
                .map(PatientAdministrativeWarningsResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    /**
     * {@code DELETE /patient-administrative-warnings/{id}} : Hard delete a warning.
     *
     * @param id the identifier of the warning to delete.
     * @return {@link ResponseEntity} with status {@code 204 (NO_CONTENT)},
     *         or {@code 404 (Not Found)} if warning does not exist.
     */
    @DeleteMapping("/patient-administrative-warnings/{id}")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        LOG.debug("REST hardDelete PatientAdministrativeWarnings id={}", id);
        patientAdministrativeWarningsService.hardDelete(id);
        return ResponseEntity.noContent().build();
    }
}

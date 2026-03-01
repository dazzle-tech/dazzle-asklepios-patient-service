package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientWarnings;
import com.dazzle.asklepios.service.PatientWarningsService;
import com.dazzle.asklepios.service.dto.PatientWarningCreateDTO;
import com.dazzle.asklepios.service.dto.PatientWarningUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class PatientWarningsController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientWarningsController.class);

    private final PatientWarningsService patientWarningsService;

    public PatientWarningsController(PatientWarningsService patientWarningsService) {
        this.patientWarningsService = patientWarningsService;
    }


    @PostMapping("/patient-warnings")
    public ResponseEntity<PatientWarnings> create(@Valid @RequestBody PatientWarningCreateDTO patientWarningCreateDTO) {
        LOG.debug("REST create Patient Warning payload={}", patientWarningCreateDTO);

        PatientWarnings created = patientWarningsService.create(patientWarningCreateDTO);

        LOG.debug("REST create Patient Warning response={}", created);

        return ResponseEntity
                .created(URI.create("/api/patient/patient-warnings/" + created.getId()))
                .body(created);
    }


    @GetMapping("/patient-warnings/by-patient/{patientId}")
    public ResponseEntity<List<PatientWarnings>> getAllWarningsByPatient(
            @PathVariable Long patientId,
            @RequestParam(name = "showCancelled", defaultValue = "false") boolean showCancelled,
            @ParameterObject Pageable pageable) {
        LOG.debug("REST get patient warnings by patientId={}, showCancelled={}", patientId, showCancelled);
        Page<PatientWarnings> page = patientWarningsService.findAllWarningsByPatientId(pageable, showCancelled, patientId);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }


    @PutMapping("/patient-warnings/{id}/cancel")
    public ResponseEntity<PatientWarnings> cancel(
            @PathVariable Long id,
            @RequestParam @NotBlank @NotEmpty @Valid String reason
    ) {
        LOG.debug("REST cancel Patient Warning : {}", id);
        return ResponseEntity.ok(
                patientWarningsService.cancel(id, reason)
        );
    }

    @PutMapping("/patient-warnings/{id}/resolve")
    public ResponseEntity<PatientWarnings> resolve(
            @PathVariable Long id
    ) {
        LOG.debug("REST resolve Patient Warning : {}", id);
        return ResponseEntity.ok(
                patientWarningsService.resolve(id)
        );
    }

    @PutMapping("/patient-warnings/{id}/undo-resolve")
    public ResponseEntity<PatientWarnings> undoResolve(
            @PathVariable Long id
    ) {
        LOG.debug("REST undo resolve Patient Warning : {}", id);
        return ResponseEntity.ok(
                patientWarningsService.undoResolve(id)
        );
    }


    @PutMapping("/patient-warnings/{id}")
    public ResponseEntity<PatientWarnings> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientWarningUpdateDTO patientWarningUpdateDTO) {

        LOG.debug("REST update Patient Warning id={} payload={}", id, patientWarningUpdateDTO);

        PatientWarnings updated = patientWarningsService.update(patientWarningUpdateDTO);
        return ResponseEntity.ok(updated);

    }




}

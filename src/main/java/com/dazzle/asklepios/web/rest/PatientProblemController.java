package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.service.PatientProblemService;
import com.dazzle.asklepios.service.dto.PatientProblems.PatientProblemCancelDTO;
import com.dazzle.asklepios.service.dto.PatientProblems.PatientProblemCreateDTO;
import com.dazzle.asklepios.service.dto.PatientProblems.PatientProblemUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/patient")
public class PatientProblemController {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientProblemController.class);

    private final PatientProblemService patientProblemService;

    public PatientProblemController(PatientProblemService service) {
        this.patientProblemService = service;
    }

    @PostMapping("/problems")
    public ResponseEntity<PatientProblem> create(
            @Valid @RequestBody PatientProblemCreateDTO patientProblemCreateDTO
    ) {
        LOG.debug("REST create PatientProblem payload={}", patientProblemCreateDTO);

        if (patientProblemCreateDTO == null) {
            throw new BadRequestAlertException(
                    "Patient problem payload is required",
                    "patientProblem",
                    "payload.required"
            );
        }

        PatientProblem created =
                patientProblemService.create(patientProblemCreateDTO);

        LOG.info("REST create PatientProblem - created id={}", created.getId());

        return ResponseEntity
                .created(URI.create("/api/patient/problems/" + created.getId()))
                .body(created);
    }

    @PutMapping("/problems")
    public ResponseEntity<PatientProblem> update(
            @Valid @RequestBody PatientProblemUpdateDTO patientProblemUpdateDTO
    ) {
        LOG.debug("REST update PatientProblem payload={}", patientProblemUpdateDTO);

        PatientProblem updated =
                patientProblemService.update(patientProblemUpdateDTO);

        LOG.info("REST update PatientProblem - updated id={}", updated.getId());

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/problems/cancel")
    public ResponseEntity<PatientProblem> cancel(
            @Valid @RequestBody PatientProblemCancelDTO patientProblemCancelDTO
    ) {
        LOG.debug("REST cancel PatientProblem payload={}", patientProblemCancelDTO);

        PatientProblem cancelled =
                patientProblemService.cancel(patientProblemCancelDTO);

        LOG.info("REST cancel PatientProblem - cancelled id={}", cancelled.getId());

        return ResponseEntity.ok(cancelled);
    }

    @DeleteMapping("/problems/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("REST delete PatientProblem id={}", id);

        patientProblemService.delete(id);

        LOG.info("REST delete PatientProblem - deleted id={}", id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/problems")
    public ResponseEntity<List<PatientProblem>> list(
            @RequestParam Long patientId,
            @RequestParam(name = "showCancelled", defaultValue = "false")
            Boolean showCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list PatientProblem patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        Page<PatientProblem> page =
                patientProblemService.findByPatientId(
                        patientId,
                        showCancelled,
                        pageable
                );

        HttpHeaders headers =
                com.dazzle.asklepios.web.rest.Helper.PaginationUtil
                        .generatePaginationHttpHeaders(
                                ServletUriComponentsBuilder.fromCurrentRequest(),
                                page
                        );

        List<PatientProblem> body = page.getContent();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
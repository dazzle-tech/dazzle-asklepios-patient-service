package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.service.PatientProblemService;
import com.dazzle.asklepios.service.dto.PatientProblems.PatientProblemCreateDTO;
import com.dazzle.asklepios.service.dto.PatientProblems.PatientProblemUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientProblems.PatientProblemResponseVM;
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
    public ResponseEntity<PatientProblemResponseVM> create(
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
        PatientProblem created = patientProblemService.create(patientProblemCreateDTO);

        return ResponseEntity
                .created(URI.create("/api/patient/problems/" + created.getId()))
                .body(PatientProblemResponseVM.ofEntity(created));
    }

    @PutMapping("/problems")
    public ResponseEntity<PatientProblemResponseVM> update(
            @Valid @RequestBody PatientProblemUpdateDTO patientProblemUpdateDTO
    ) {
        PatientProblem updated = patientProblemService.update(patientProblemUpdateDTO);

        return ResponseEntity.ok(
                PatientProblemResponseVM.ofEntity(updated)
        );
    }


    @DeleteMapping("/problems/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientProblemService.delete(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/problems")
    public ResponseEntity<List<PatientProblemResponseVM>> list(
            @RequestParam Long patientId,
            @ParameterObject Pageable pageable
    ) {
        Page<PatientProblem> page =
                patientProblemService.findByPatientId(patientId, pageable);

        HttpHeaders headers =
                com.dazzle.asklepios.web.rest.Helper.PaginationUtil
                        .generatePaginationHttpHeaders(
                                ServletUriComponentsBuilder.fromCurrentRequest(),
                                page
                        );

        List<PatientProblemResponseVM> body =
                page.getContent()
                        .stream()
                        .map(PatientProblemResponseVM::ofEntity)
                        .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}

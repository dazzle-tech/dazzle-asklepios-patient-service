package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ExternalTest;
import com.dazzle.asklepios.repository.ExternalTestRepository;
import com.dazzle.asklepios.service.ExternalTestService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.sendtest.ExternalTestDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.ExternalTestResponseVM;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/patient")
public class ExternalTestController {

    private final ExternalTestService externalTestService;
    private final ExternalTestRepository externalTestRepository;

    public ExternalTestController(
            ExternalTestService externalTestService,
            ExternalTestRepository externalTestRepository
    ) {
        this.externalTestService = externalTestService;
        this.externalTestRepository = externalTestRepository;
    }

    // create api/patient/external-test
    @PostMapping("/external-test")
    public ResponseEntity<ExternalTestResponseVM> create(@Valid @RequestBody ExternalTestDTO dto) {

        // validate already exists (friendly message before DB unique)
        if (externalTestRepository.existsByTestId(dto.testId())) {
            throw new BadRequestAlertException(
                    "already_exists",
                    "external_test",
                    "External test already exists for testId " + dto.testId()
            );
        }

        // validate diagnostic_test exists + save
        ExternalTest saved;
        try {
            saved = externalTestService.create(dto);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException(
                    "bad_request",
                    "external_test",
                    ex.getMessage()
            );
        }

        return ResponseEntity
                .created(URI.create("/api/patient/external-test/" + saved.getTestId()))
                .body(ExternalTestResponseVM.ofEntity(saved));
    }

    // getByTestId api/patient/external-test/{id}  (id = testId)
    @GetMapping("/external-test/{id}")
    public ResponseEntity<ExternalTestResponseVM> getByTestId(@PathVariable("id") Long testId) {
        ExternalTest existing = externalTestRepository.findByTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "external_test",
                        "ExternalTest not found with testId " + testId
                ));
        return ResponseEntity.ok(ExternalTestResponseVM.ofEntity(existing));
    }

    // deleteByTestId api/patient/external-test/{testId}
    @DeleteMapping("/external-test/{testId}")
    public ResponseEntity<Void> deleteByTestId(@PathVariable Long testId) {
        ExternalTest existing = externalTestRepository.findByTestId(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "external_test",
                        "ExternalTest not found with testId " + testId
                ));

        externalTestService.deleteByTestId(existing.getTestId());
        return ResponseEntity.noContent().build();
    }
}


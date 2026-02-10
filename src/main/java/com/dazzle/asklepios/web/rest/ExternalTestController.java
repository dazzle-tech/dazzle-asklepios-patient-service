package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ExternalTest;
import com.dazzle.asklepios.service.ExternalTestService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.sendtest.ExternalTestDTO;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.ExternalTestResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(ExternalTestController.class);

    private final ExternalTestService externalTestService;

    public ExternalTestController(
            ExternalTestService externalTestService
    ) {
        this.externalTestService = externalTestService;
    }

    @PostMapping("/external-test")
    public ResponseEntity<ExternalTestResponseVM> create(@Valid @RequestBody ExternalTestDTO dto) {
        LOG.debug("[ExternalTest] CREATE - request received. payload={}", dto);
        ExternalTest saved = externalTestService.create(dto);

        LOG.debug("[ExternalTest] CREATE - created successfully. testId={}", saved.getTestId());
        return ResponseEntity
                .created(URI.create("/api/patient/external-test/" + saved.getTestId()))
                .body(ExternalTestResponseVM.ofEntity(saved));
    }

    @GetMapping("/external-test/{id}")
    public ResponseEntity<ExternalTestResponseVM> getByTestId(@Valid @PathVariable("id") Long testId) {
        LOG.debug("[ExternalTest] GET_BY_TEST_ID - request received. testId={}", testId);
        ExternalTest existing = externalTestService.getByTestId(testId);
        LOG.debug("[ExternalTest] GET_BY_TEST_ID - found. testId={}", existing.getTestId());
        return ResponseEntity.ok(ExternalTestResponseVM.ofEntity(existing));
    }

    @DeleteMapping("/external-test/{testId}")
    public ResponseEntity<Void> deleteByTestId(@Valid @PathVariable Long testId) {
        LOG.debug("[ExternalTest] DELETE - request received. testId={}", testId);
        externalTestService.deleteByTestId(testId);
        LOG.debug("[ExternalTest] DELETE - deleted successfully. testId={}", testId);
        return ResponseEntity.noContent().build();
    }
}


package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PatientDiagnosticResultHistoryService;
import com.dazzle.asklepios.web.rest.vm.laboratory.ProfileTestGroupedHistoryVM;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientDiagnosticResultHistoryController {

    private final PatientDiagnosticResultHistoryService diagnosticResultHistoryService;

    public PatientDiagnosticResultHistoryController(PatientDiagnosticResultHistoryService historyService) {
        this.diagnosticResultHistoryService = historyService;
    }

    @GetMapping("/diagnostic-test-results-history/{patientId}")
    public ResponseEntity<List<ProfileTestGroupedHistoryVM>> getPatientResultsHistory(
            @PathVariable Long patientId,
            @RequestParam @NotNull Instant from,
            @RequestParam @NotNull Instant to,
            @RequestParam(required = false) Long profileTestId
    ) {
        return ResponseEntity.ok(diagnosticResultHistoryService.getGroupedHistory(patientId, from, to, profileTestId));
    }
}

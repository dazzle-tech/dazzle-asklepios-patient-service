package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PatientFinancialStatementService;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse;
import com.dazzle.asklepios.service.dto.accounting.PatientFinancialDashboardResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing/financial-statement")
public class PatientFinancialStatementController {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientFinancialStatementController.class);

    private final PatientFinancialStatementService patientFinancialStatementService;

    @GetMapping("/patients/{patientId}/dashboard")
    public ResponseEntity<PatientFinancialDashboardResponse> getPatientDashboard(
            @PathVariable("patientId") @NotNull Long patientId
    ) {
        LOG.debug("REST request patient financial dashboard patientId={}", patientId);
        return ResponseEntity.ok(
                patientFinancialStatementService.getPatientDashboard(patientId)
        );
    }

    @GetMapping("/encounters/{encounterId}")
    public ResponseEntity<EncounterFinancialStatementResponse> getEncounterStatement(
            @PathVariable("encounterId") @NotNull Long encounterId
    ) {
        LOG.debug("REST request encounter financial statement encounterId={}", encounterId);
        return ResponseEntity.ok(
                patientFinancialStatementService.getEncounterStatement(encounterId)
        );
    }
}

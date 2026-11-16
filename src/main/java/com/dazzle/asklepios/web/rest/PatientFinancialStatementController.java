package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PatientFinancialStatementService;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.AuditRow;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.ReceiptRow;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.ServiceLine;
import com.dazzle.asklepios.service.dto.accounting.EncounterFinancialStatementResponse.TimelineRow;
import com.dazzle.asklepios.service.dto.accounting.PagedResponse;
import com.dazzle.asklepios.service.dto.accounting.PatientFinancialDashboardResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
            @PathVariable("patientId") @NotNull Long patientId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        LOG.debug(
                "REST request patient financial dashboard patientId={} page={} size={}",
                patientId,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );
        return ResponseEntity.ok(
                patientFinancialStatementService.getPatientDashboard(patientId, pageable)
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

    @GetMapping("/encounters/{encounterId}/service-lines")
    public ResponseEntity<PagedResponse<ServiceLine>> getEncounterServiceLines(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(
                patientFinancialStatementService.getEncounterServiceLines(encounterId, pageable)
        );
    }

    @GetMapping("/encounters/{encounterId}/receipts")
    public ResponseEntity<PagedResponse<ReceiptRow>> getEncounterReceipts(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(
                patientFinancialStatementService.getEncounterReceipts(encounterId, pageable)
        );
    }

    @GetMapping("/encounters/{encounterId}/timeline")
    public ResponseEntity<PagedResponse<TimelineRow>> getEncounterTimeline(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(
                patientFinancialStatementService.getEncounterTimeline(encounterId, pageable)
        );
    }

    @GetMapping("/encounters/{encounterId}/audit-trail")
    public ResponseEntity<PagedResponse<AuditRow>> getEncounterAuditTrail(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @PageableDefault(size = 5) Pageable pageable
    ) {
        return ResponseEntity.ok(
                patientFinancialStatementService.getEncounterAuditTrail(encounterId, pageable)
        );
    }
}

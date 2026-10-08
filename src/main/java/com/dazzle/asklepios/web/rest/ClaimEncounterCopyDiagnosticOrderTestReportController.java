package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestReport;
import com.dazzle.asklepios.service.ClaimEncounterCopyDiagnosticOrderTestReportService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestReportCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestReportCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestReportUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/patient/billing/claim-encounter-copy-diagnostic-order-test-reports"
)
@RequiredArgsConstructor
public class ClaimEncounterCopyDiagnosticOrderTestReportController {

    private final ClaimEncounterCopyDiagnosticOrderTestReportService service;

    @GetMapping(
            "/by-copy/{claimEncounterCopyId}/not-cancelled"
    )
    public ResponseEntity<List<ClaimEncounterCopyDiagnosticOrderTestReport>>
    findByCopyId(
            @PathVariable Long claimEncounterCopyId
    ) {
        return ResponseEntity.ok(
                service.findByCopyId(
                        claimEncounterCopyId,
                        false
                )
        );
    }

    @GetMapping(
            "/by-copy/{claimEncounterCopyId}/all"
    )
    public ResponseEntity<List<ClaimEncounterCopyDiagnosticOrderTestReport>>
    findAllByCopyId(
            @PathVariable Long claimEncounterCopyId
    ) {
        return ResponseEntity.ok(
                service.findByCopyId(
                        claimEncounterCopyId,
                        true
                )
        );
    }

    @PostMapping(
            "/by-copy/{claimEncounterCopyId}"
    )
    public ResponseEntity<ClaimEncounterCopyDiagnosticOrderTestReport> create(
            @PathVariable Long claimEncounterCopyId,
            @Valid @RequestBody
            ClaimEncounterCopyDiagnosticOrderTestReportCreateDTO dto
    ) {
        return ResponseEntity.ok(
                service.create(
                        claimEncounterCopyId,
                        dto
                )
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterCopyDiagnosticOrderTestReport> update(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyDiagnosticOrderTestReportUpdateDTO dto
    ) {
        return ResponseEntity.ok(
                service.update(id, dto)
        );
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterCopyDiagnosticOrderTestReport> cancel(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyDiagnosticOrderTestReportCancelDTO dto
    ) {
        return ResponseEntity.ok(
                service.cancel(id, dto)
        );
    }
}

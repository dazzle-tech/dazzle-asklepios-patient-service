package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestResult;
import com.dazzle.asklepios.service.ClaimEncounterCopyDiagnosticOrderTestResultService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestResultCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyDiagnosticOrderTestResultUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/patient/billing/claim-encounter-copy-diagnostic-order-test-results"
)
@RequiredArgsConstructor
public class ClaimEncounterCopyDiagnosticOrderTestResultController {

    private final ClaimEncounterCopyDiagnosticOrderTestResultService service;

    @GetMapping(
            "/by-copy/{claimEncounterCopyId}/not-cancelled"
    )
    public ResponseEntity<List<ClaimEncounterCopyDiagnosticOrderTestResult>>
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
    public ResponseEntity<List<ClaimEncounterCopyDiagnosticOrderTestResult>>
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
    public ResponseEntity<ClaimEncounterCopyDiagnosticOrderTestResult> create(
            @PathVariable Long claimEncounterCopyId,
            @Valid @RequestBody
            ClaimEncounterCopyDiagnosticOrderTestResultCreateDTO dto
    ) {
        return ResponseEntity.ok(
                service.create(
                        claimEncounterCopyId,
                        dto
                )
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterCopyDiagnosticOrderTestResult> update(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyDiagnosticOrderTestResultUpdateDTO dto
    ) {
        return ResponseEntity.ok(
                service.update(id, dto)
        );
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterCopyDiagnosticOrderTestResult> cancel(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyDiagnosticOrderTestResultCancelDTO dto
    ) {
        return ResponseEntity.ok(
                service.cancel(id, dto)
        );
    }
}

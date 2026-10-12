
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.ClaimEncounterDiagnosisService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterDiagnosisRequest;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterDiagnosisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing/claim-encounter-diagnoses")
@Validated
public class ClaimEncounterDiagnosisController {

    private final ClaimEncounterDiagnosisService claimEncounterDiagnosisService;

    @GetMapping("/encounters/{encounterId}")
    public ResponseEntity<List<ClaimEncounterDiagnosisResponse>> getByEncounterId(
            @PathVariable("encounterId") @NotNull Long encounterId
    ) {
        return ResponseEntity.ok(
                claimEncounterDiagnosisService.findByEncounterId(encounterId)
        );
    }

    @PostMapping("/encounters/{encounterId}")
    public ResponseEntity<ClaimEncounterDiagnosisResponse> create(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @Valid @RequestBody ClaimEncounterDiagnosisRequest request
    ) {
        ClaimEncounterDiagnosisResponse response =
                claimEncounterDiagnosisService.create(encounterId, request);

        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterDiagnosisResponse> update(
            @PathVariable("id") @NotNull Long id,
            @Valid @RequestBody ClaimEncounterDiagnosisRequest request
    ) {
        ClaimEncounterDiagnosisResponse response =
                claimEncounterDiagnosisService.update(id, request);

        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") @NotNull Long id
    ) {
        boolean deleted = claimEncounterDiagnosisService.delete(id);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}

package com.dazzle.asklepios.web.rest.billing;

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

    @PutMapping("/encounters/{encounterId}")
    public ResponseEntity<List<ClaimEncounterDiagnosisResponse>> update(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @Valid @RequestBody List<ClaimEncounterDiagnosisRequest> requests
    ) {
        return ResponseEntity.ok(
                claimEncounterDiagnosisService.update(
                        encounterId,
                        requests
                )
        );
    }
}
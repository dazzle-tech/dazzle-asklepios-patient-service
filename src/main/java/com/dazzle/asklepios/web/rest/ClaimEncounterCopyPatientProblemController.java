package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterCopyPatientProblem;
import com.dazzle.asklepios.service.ClaimEncounterCopyPatientProblemService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyPatientProblemCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyPatientProblemCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyPatientProblemUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient/billing/claim-encounter-copy-patient-problems")
@RequiredArgsConstructor
public class ClaimEncounterCopyPatientProblemController {

    private final ClaimEncounterCopyPatientProblemService service;

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterCopyPatientProblem> update(
            @PathVariable Long id,
            @Valid @RequestBody ClaimEncounterCopyPatientProblemUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterCopyPatientProblem> cancel(
            @PathVariable Long id,
            @Valid @RequestBody ClaimEncounterCopyPatientProblemCancelDTO dto
    ) {
        return ResponseEntity.ok(service.cancel(id, dto));
    }

    @GetMapping("/by-copy/{claimEncounterCopyId}/not-cancelled")
    public ResponseEntity<List<ClaimEncounterCopyPatientProblem>> findByCopyId(
            @PathVariable Long claimEncounterCopyId
    ) {
        return ResponseEntity.ok(
                service.findByCopyId(
                        claimEncounterCopyId,
                        false
                )
        );
    }

    @GetMapping("/by-copy/{claimEncounterCopyId}/all")
    public ResponseEntity<List<ClaimEncounterCopyPatientProblem>> findAllByCopyId(
            @PathVariable Long claimEncounterCopyId
    ) {
        return ResponseEntity.ok(
                service.findByCopyId(
                        claimEncounterCopyId,
                        true
                )
        );
    }

    @PostMapping("/by-copy/{claimEncounterCopyId}")
    public ResponseEntity<ClaimEncounterCopyPatientProblem> create(
            @PathVariable Long claimEncounterCopyId,
            @Valid @RequestBody ClaimEncounterCopyPatientProblemCreateDTO dto
    ) {
        return ResponseEntity.ok(
                service.create(
                        claimEncounterCopyId,
                        dto
                )
        );
    }
}
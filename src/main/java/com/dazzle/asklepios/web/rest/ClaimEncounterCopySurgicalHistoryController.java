package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterCopySurgicalHistory;
import com.dazzle.asklepios.service.ClaimEncounterCopySurgicalHistoryService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySurgicalHistoryCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySurgicalHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySurgicalHistoryUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient/billing/claim-encounter-copy-surgical-histories")
@RequiredArgsConstructor
public class ClaimEncounterCopySurgicalHistoryController {

    private final ClaimEncounterCopySurgicalHistoryService service;

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterCopySurgicalHistory> update(
            @PathVariable Long id,
            @Valid @RequestBody ClaimEncounterCopySurgicalHistoryUpdateDTO dto
    ) {
        return ResponseEntity.ok(
                service.update(id, dto)
        );
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterCopySurgicalHistory> cancel(
            @PathVariable Long id,
            @Valid @RequestBody ClaimEncounterCopySurgicalHistoryCancelDTO dto
    ) {
        return ResponseEntity.ok(
                service.cancel(id, dto)
        );
    }

    @GetMapping("/by-copy/{claimEncounterCopyId}/not-cancelled")
    public ResponseEntity<List<ClaimEncounterCopySurgicalHistory>> findByCopyId(
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
    public ResponseEntity<List<ClaimEncounterCopySurgicalHistory>> findAllByCopyId(
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
    public ResponseEntity<ClaimEncounterCopySurgicalHistory> create(
            @PathVariable Long claimEncounterCopyId,
            @Valid @RequestBody ClaimEncounterCopySurgicalHistoryCreateDTO dto
    ) {
        return ResponseEntity.ok(
                service.create(
                        claimEncounterCopyId,
                        dto
                )
        );
    }
}
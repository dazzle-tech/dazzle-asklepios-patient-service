package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterCopyFamilyHistory;
import com.dazzle.asklepios.service.ClaimEncounterCopyFamilyHistoryService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyFamilyHistoryCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyFamilyHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyFamilyHistoryUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/patient/billing/claim-encounter-copy-family-histories"
)
@RequiredArgsConstructor
public class ClaimEncounterCopyFamilyHistoryController {

    private final ClaimEncounterCopyFamilyHistoryService service;

    @GetMapping(
            "/by-copy/{claimEncounterCopyId}/not-cancelled"
    )
    public ResponseEntity<List<ClaimEncounterCopyFamilyHistory>>
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
    public ResponseEntity<List<ClaimEncounterCopyFamilyHistory>>
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
    public ResponseEntity<ClaimEncounterCopyFamilyHistory> create(
            @PathVariable Long claimEncounterCopyId,
            @Valid @RequestBody
            ClaimEncounterCopyFamilyHistoryCreateDTO dto
    ) {
        return ResponseEntity.ok(
                service.create(
                        claimEncounterCopyId,
                        dto
                )
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterCopyFamilyHistory> update(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyFamilyHistoryUpdateDTO dto
    ) {
        return ResponseEntity.ok(
                service.update(id, dto)
        );
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterCopyFamilyHistory> cancel(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyFamilyHistoryCancelDTO dto
    ) {
        return ResponseEntity.ok(
                service.cancel(id, dto)
        );
    }
}
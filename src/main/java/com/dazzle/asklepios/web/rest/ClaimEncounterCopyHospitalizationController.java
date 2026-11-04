package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterCopyHospitalization;
import com.dazzle.asklepios.service.ClaimEncounterCopyHospitalizationService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyHospitalizationCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyHospitalizationCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyHospitalizationUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient/billing/claim-encounter-copy-hospitalizations")
@RequiredArgsConstructor
public class ClaimEncounterCopyHospitalizationController {

    private final ClaimEncounterCopyHospitalizationService
            claimEncounterCopyHospitalizationService;

    @GetMapping("/by-copy/{copyId}/not-cancelled")
    public ResponseEntity<List<ClaimEncounterCopyHospitalization>>
    getByCopyId(
            @PathVariable Long copyId
    ) {
        return ResponseEntity.ok(
                claimEncounterCopyHospitalizationService.findByCopyId(
                        copyId,
                        false
                )
        );
    }

    @GetMapping("/by-copy/{copyId}/all")
    public ResponseEntity<List<ClaimEncounterCopyHospitalization>>
    getAllByCopyId(
            @PathVariable Long copyId
    ) {
        return ResponseEntity.ok(
                claimEncounterCopyHospitalizationService.findByCopyId(
                        copyId,
                        true
                )
        );
    }

    @PostMapping("/by-copy/{copyId}")
    public ResponseEntity<ClaimEncounterCopyHospitalization> create(
            @PathVariable Long copyId,
            @Valid @RequestBody
            ClaimEncounterCopyHospitalizationCreateDTO dto
    ) {
        return ResponseEntity.ok(
                claimEncounterCopyHospitalizationService.create(
                        copyId,
                        dto
                )
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterCopyHospitalization> update(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyHospitalizationUpdateDTO dto
    ) {
        return ResponseEntity.ok(
                claimEncounterCopyHospitalizationService.update(
                        id,
                        dto
                )
        );
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterCopyHospitalization> cancel(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyHospitalizationCancelDTO dto
    ) {
        return ResponseEntity.ok(
                claimEncounterCopyHospitalizationService.cancel(
                        id,
                        dto
                )
        );
    }
}
        package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ClaimEncounterCopyCurrentMedication;
import com.dazzle.asklepios.service.ClaimEncounterCopyCurrentMedicationService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyCurrentMedicationCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyCurrentMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyCurrentMedicationUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient/billing/claim-encounter-copy-current-medications")
@RequiredArgsConstructor
public class ClaimEncounterCopyCurrentMedicationController {

    private final ClaimEncounterCopyCurrentMedicationService
            claimEncounterCopyCurrentMedicationService;

    @GetMapping("/by-copy/{copyId}/not-cancelled")
    public ResponseEntity<List<ClaimEncounterCopyCurrentMedication>>
    getByCopyId(@PathVariable Long copyId) {
        return ResponseEntity.ok(
                claimEncounterCopyCurrentMedicationService.findByCopyId(
                        copyId,
                        false
                )
        );
    }

    @GetMapping("/by-copy/{copyId}/all")
    public ResponseEntity<List<ClaimEncounterCopyCurrentMedication>>
    getAllByCopyId(@PathVariable Long copyId) {
        return ResponseEntity.ok(
                claimEncounterCopyCurrentMedicationService.findByCopyId(
                        copyId,
                        true
                )
        );
    }

    @PostMapping("/by-copy/{copyId}")
    public ResponseEntity<ClaimEncounterCopyCurrentMedication> create(
            @PathVariable Long copyId,
            @Valid @RequestBody
            ClaimEncounterCopyCurrentMedicationCreateDTO dto
    ) {
        return ResponseEntity.ok(
                claimEncounterCopyCurrentMedicationService.create(
                        copyId,
                        dto
                )
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimEncounterCopyCurrentMedication> update(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyCurrentMedicationUpdateDTO dto
    ) {
        return ResponseEntity.ok(
                claimEncounterCopyCurrentMedicationService.update(
                        id,
                        dto
                )
        );
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ClaimEncounterCopyCurrentMedication> cancel(
            @PathVariable Long id,
            @Valid @RequestBody
            ClaimEncounterCopyCurrentMedicationCancelDTO dto
    ) {
        return ResponseEntity.ok(
                claimEncounterCopyCurrentMedicationService.cancel(
                        id,
                        dto
                )
        );
    }
}

package com.dazzle.asklepios.web.rest;


import com.dazzle.asklepios.service.ClaimEncounterCopyService;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing/claim-encounter-copies")
public class ClaimEncounterCopyController {

    private final ClaimEncounterCopyService claimEncounterCopyService;

    @GetMapping("/encounters/{encounterId}")
    public ResponseEntity<ClaimEncounterCopyResponse> getByEncounterId(
            @PathVariable("encounterId") @NotNull Long encounterId
    ) {
        return ResponseEntity.of(
                java.util.Optional.ofNullable(
                        claimEncounterCopyService.findByEncounterId(encounterId)
                )
        );
    }

    @PutMapping("/encounters/{encounterId}")
    public ResponseEntity<ClaimEncounterCopyResponse> update(
            @PathVariable("encounterId") @NotNull Long encounterId,
            @Valid @RequestBody ClaimEncounterCopyUpdateRequest request
    ) {
        return ResponseEntity.of(
                java.util.Optional.ofNullable(
                        claimEncounterCopyService.update(
                                encounterId,
                                request
                        )
                )
        );
    }
}
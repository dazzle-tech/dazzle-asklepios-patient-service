package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DialysisSessionService;
import com.dazzle.asklepios.service.dto.DialysisSessionCreateDTO;
import com.dazzle.asklepios.service.dto.DialysisSessionUpdateDTO;
import com.dazzle.asklepios.service.vm.DialysisSessionResponseVM;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient/dialysis-sessions")
@RequiredArgsConstructor
public class DialysisSessionController {

    private final DialysisSessionService service;

    @PostMapping
    public ResponseEntity<DialysisSessionResponseVM> create(
            @Valid @RequestBody DialysisSessionCreateDTO dto
    ) {
        DialysisSessionResponseVM response =
                service.create(dto);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<DialysisSessionResponseVM> update(
            @Valid @RequestBody DialysisSessionUpdateDTO dto
    ) {
        DialysisSessionResponseVM response =
                service.update(dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-patient-and-encounter")
    public ResponseEntity<DialysisSessionResponseVM>
    findByPatientAndEncounter(
            @RequestParam Long patientId,
            @RequestParam Long encounterId
    ) {

        return service
                .findByPatientAndEncounter(patientId, encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}

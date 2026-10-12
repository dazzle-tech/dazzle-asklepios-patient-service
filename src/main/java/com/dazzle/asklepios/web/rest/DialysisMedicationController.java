package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DialysisMedicationService;
import com.dazzle.asklepios.service.dto.DialysisMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.DialysisMedicationUpdateDTO;
import com.dazzle.asklepios.service.vm.DialysisMedicationResponseVM;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient/dialysis-medications")
@RequiredArgsConstructor
public class DialysisMedicationController {

    private final DialysisMedicationService service;

    @PostMapping
    public ResponseEntity<DialysisMedicationResponseVM> create(
            @Valid @RequestBody DialysisMedicationCreateDTO dto
    ) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping
    public ResponseEntity<DialysisMedicationResponseVM> update(
            @Valid @RequestBody DialysisMedicationUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.update(dto));
    }

    @GetMapping("/by-dialysis-session")
    public ResponseEntity<List<DialysisMedicationResponseVM>>
    findByDialysisSession(
            @RequestParam Long dialysisSessionId
    ) {
        return ResponseEntity.ok(
                service.findByDialysisSessionId(dialysisSessionId)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

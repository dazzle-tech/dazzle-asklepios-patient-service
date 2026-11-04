package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.NephrologyKidneyAssessmentService;
import com.dazzle.asklepios.service.dto.NephrologyKidneyAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.NephrologyKidneyAssessmentUpdateDTO;
import com.dazzle.asklepios.service.vm.NephrologyKidneyAssessmentResponseVM;
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
@RequestMapping("/api/patient/nephrology-kidney-assessments")
@RequiredArgsConstructor
public class NephrologyKidneyAssessmentController {

    private final NephrologyKidneyAssessmentService service;

    @PostMapping
    public ResponseEntity<NephrologyKidneyAssessmentResponseVM> create(
            @Valid @RequestBody NephrologyKidneyAssessmentCreateDTO dto
    ) {
        NephrologyKidneyAssessmentResponseVM response =
                service.create(dto);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<NephrologyKidneyAssessmentResponseVM> update(
            @Valid @RequestBody NephrologyKidneyAssessmentUpdateDTO dto
    ) {
        NephrologyKidneyAssessmentResponseVM response =
                service.update(dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-patient-and-encounter")
    public ResponseEntity<NephrologyKidneyAssessmentResponseVM>
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
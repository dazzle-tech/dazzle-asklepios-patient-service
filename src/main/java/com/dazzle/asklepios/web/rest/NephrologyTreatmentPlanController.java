package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.NephrologyTreatmentPlanService;
import com.dazzle.asklepios.service.dto.NephrologyTreatmentPlanCreateDTO;
import com.dazzle.asklepios.service.dto.NephrologyTreatmentPlanUpdateDTO;
import com.dazzle.asklepios.service.vm.NephrologyTreatmentPlanResponseVM;
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
@RequestMapping("/api/patient/nephrology-treatment-plans")
@RequiredArgsConstructor
public class NephrologyTreatmentPlanController {

    private final NephrologyTreatmentPlanService service;

    @PostMapping
    public ResponseEntity<NephrologyTreatmentPlanResponseVM> create(
            @Valid @RequestBody NephrologyTreatmentPlanCreateDTO dto
    ) {
        NephrologyTreatmentPlanResponseVM response =
                service.create(dto);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<NephrologyTreatmentPlanResponseVM> update(
            @Valid @RequestBody NephrologyTreatmentPlanUpdateDTO dto
    ) {
        NephrologyTreatmentPlanResponseVM response =
                service.update(dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-patient-and-encounter")
    public ResponseEntity<NephrologyTreatmentPlanResponseVM>
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

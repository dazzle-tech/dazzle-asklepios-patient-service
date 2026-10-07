package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.NephrologyRenalFunctionService;
import com.dazzle.asklepios.service.dto.NephrologyRenalFunctionCreateDTO;
import com.dazzle.asklepios.service.dto.NephrologyRenalFunctionUpdateDTO;
import com.dazzle.asklepios.service.vm.NephrologyRenalFunctionResponseVM;
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
@RequestMapping("/api/patient/nephrology-renal-functions")
@RequiredArgsConstructor
public class NephrologyRenalFunctionController {

    private final NephrologyRenalFunctionService service;

    @PostMapping
    public ResponseEntity<NephrologyRenalFunctionResponseVM> create(
            @Valid @RequestBody NephrologyRenalFunctionCreateDTO dto
    ) {
        NephrologyRenalFunctionResponseVM response =
                service.create(dto);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<NephrologyRenalFunctionResponseVM> update(
            @Valid @RequestBody NephrologyRenalFunctionUpdateDTO dto
    ) {
        NephrologyRenalFunctionResponseVM response =
                service.update(dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-patient-and-encounter")
    public ResponseEntity<NephrologyRenalFunctionResponseVM>
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

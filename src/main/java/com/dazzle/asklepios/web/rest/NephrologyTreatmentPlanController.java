package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.NephrologyTreatmentPlanService;
import com.dazzle.asklepios.service.dto.NephrologyTreatmentPlanCreateDTO;
import com.dazzle.asklepios.service.dto.NephrologyTreatmentPlanUpdateDTO;
import com.dazzle.asklepios.service.vm.NephrologyTreatmentPlanResponseVM;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<NephrologyTreatmentPlanResponseVM>> findByPatientId(
            @RequestParam Long patientId,
            @RequestParam(required = false) Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        Page<NephrologyTreatmentPlanResponseVM> page =
                service.findByPatientId(patientId, encounterId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NephrologyTreatmentPlanResponseVM> findById(
            @PathVariable Long id
    ) {
        return service
                .findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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

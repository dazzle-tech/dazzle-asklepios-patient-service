package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DialysisSessionService;
import com.dazzle.asklepios.service.dto.DialysisSessionCreateDTO;
import com.dazzle.asklepios.service.dto.DialysisSessionUpdateDTO;
import com.dazzle.asklepios.service.vm.DialysisSessionResponseVM;
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

    @GetMapping
    public ResponseEntity<List<DialysisSessionResponseVM>> findByPatientAndEncounter(
            @RequestParam Long patientId,
            @RequestParam Long encounterId,
            @RequestParam(required = false) Long treatmentPlanId,
            @ParameterObject Pageable pageable
    ) {
        Page<DialysisSessionResponseVM> page =
                service.findByPatientAndEncounter(
                        patientId,
                        encounterId,
                        treatmentPlanId,
                        pageable
                );

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DialysisSessionResponseVM> findById(
            @PathVariable Long id
    ) {
        return service
                .findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-patient-encounter-and-treatment-plan")
    public ResponseEntity<List<DialysisSessionResponseVM>>
    findByPatientEncounterAndTreatmentPlan(
            @RequestParam Long patientId,
            @RequestParam Long encounterId,
            @RequestParam Long treatmentPlanId
    ) {
        List<DialysisSessionResponseVM> sessions =
                service.findAllByPatientEncounterAndTreatmentPlan(
                        patientId,
                        encounterId,
                        treatmentPlanId
                );

        return ResponseEntity.ok(sessions);
    }

    /**
     * Legacy single-session endpoint. Returns the most recently modified active
     * session for the patient/encounter, or 204 when none exists.
     */
    @GetMapping("/by-patient-and-encounter")
    public ResponseEntity<DialysisSessionResponseVM>
    findByPatientAndEncounterLegacy(
            @RequestParam Long patientId,
            @RequestParam Long encounterId
    ) {

        return service
                .findByPatientAndEncounter(patientId, encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}

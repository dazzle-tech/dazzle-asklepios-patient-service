package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DentalPlannedTreatmentService;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentBatchSaveDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentCancelDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentExecutionDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentSaveDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DentalPlannedTreatmentController {

    private static final Logger LOG = LoggerFactory.getLogger(DentalPlannedTreatmentController.class);

    private final DentalPlannedTreatmentService dentalPlannedTreatmentService;

    public DentalPlannedTreatmentController(DentalPlannedTreatmentService dentalPlannedTreatmentService) {
        this.dentalPlannedTreatmentService = dentalPlannedTreatmentService;
    }

    @GetMapping("/dental/treatment-plan/by-plan/{planId}")
    public ResponseEntity<List<PlannedTreatmentDTO>> getTreatmentsForPlan(@PathVariable Long planId) {
        LOG.debug("REST get planned treatments for planId={}", planId);
        return ResponseEntity.ok(dentalPlannedTreatmentService.listForPlan(planId));
    }

    @GetMapping("/dental/treatment-plan/by-encounter/{encounterId}")
    public ResponseEntity<DentalTreatmentPlanResponseDTO> getTreatmentPlanForEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get treatment plan for encounterId={}", encounterId);
        return ResponseEntity.ok(dentalPlannedTreatmentService.getTreatmentPlanForEncounter(encounterId));
    }

    @GetMapping("/dental/treatment-plan/sessions/by-patient/{patientId}")
    public ResponseEntity<List<Integer>> listPatientSessionNumbers(@PathVariable Long patientId) {
        LOG.debug("REST list treatment sessions for patientId={}", patientId);
        return ResponseEntity.ok(dentalPlannedTreatmentService.listPatientSessionNumbers(patientId));
    }

    @PostMapping("/dental/treatment-plan/{id}/start")
    public ResponseEntity<PlannedTreatmentDTO> startPlannedTreatment(
            @PathVariable Long id,
            @Valid @RequestBody PlannedTreatmentExecutionDTO dto
    ) {
        LOG.debug("REST start planned treatment id={}", id);
        return ResponseEntity.ok(
                dentalPlannedTreatmentService.startPlannedTreatment(id, dto.executionEncounterId())
        );
    }

    @PostMapping("/dental/treatment-plan")
    public ResponseEntity<PlannedTreatmentDTO> createPlannedTreatment(@Valid @RequestBody PlannedTreatmentSaveDTO dto) {
        LOG.debug("REST create planned treatment payload={}", dto);
        PlannedTreatmentDTO saved = dentalPlannedTreatmentService.createPlannedTreatment(dto);
        return ResponseEntity
                .created(java.net.URI.create("/api/patient/dental/treatment-plan/" + saved.id()))
                .body(saved);
    }

    @PostMapping("/dental/treatment-plan/batch")
    public ResponseEntity<List<PlannedTreatmentDTO>> createPlannedTreatmentsBatch(
            @Valid @RequestBody PlannedTreatmentBatchSaveDTO dto
    ) {
        LOG.debug("REST create planned treatment batch toothCount={}", dto.toothIds() != null ? dto.toothIds().size() : 0);
        List<PlannedTreatmentDTO> saved = dentalPlannedTreatmentService.createPlannedTreatmentsBatch(dto);
        return ResponseEntity
                .created(java.net.URI.create("/api/patient/dental/treatment-plan/batch"))
                .body(saved);
    }

    @PutMapping("/dental/treatment-plan/{id}")
    public ResponseEntity<PlannedTreatmentDTO> updatePlannedTreatment(
            @PathVariable Long id,
            @Valid @RequestBody PlannedTreatmentSaveDTO dto
    ) {
        LOG.debug("REST update planned treatment id={} payload={}", id, dto);
        return ResponseEntity.ok(dentalPlannedTreatmentService.updatePlannedTreatment(id, dto));
    }

    @PostMapping("/dental/treatment-plan/{id}/cancel")
    public ResponseEntity<PlannedTreatmentDTO> cancelPlannedTreatment(
            @PathVariable Long id,
            @Valid @RequestBody PlannedTreatmentCancelDTO dto
    ) {
        LOG.debug("REST cancel planned treatment id={}", id);
        return ResponseEntity.ok(
                dentalPlannedTreatmentService.cancelPlannedTreatment(id, dto.cancellationReason())
        );
    }

    @PostMapping("/dental/treatment-plan/{id}/complete")
    public ResponseEntity<PlannedTreatmentDTO> completePlannedTreatment(
            @PathVariable Long id,
            @Valid @RequestBody PlannedTreatmentExecutionDTO dto
    ) {
        LOG.debug("REST complete planned treatment id={}", id);
        return ResponseEntity.ok(
                dentalPlannedTreatmentService.completePlannedTreatment(id, dto.executionEncounterId())
        );
    }
}

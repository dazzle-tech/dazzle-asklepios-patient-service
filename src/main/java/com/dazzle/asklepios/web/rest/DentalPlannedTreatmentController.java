package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DentalPlannedTreatmentService;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentCancelDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentDTO;
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

@RestController
@RequestMapping("/api/patient/dental/treatment-plan")
public class DentalPlannedTreatmentController {

    private static final Logger LOG = LoggerFactory.getLogger(DentalPlannedTreatmentController.class);

    private final DentalPlannedTreatmentService dentalPlannedTreatmentService;

    public DentalPlannedTreatmentController(DentalPlannedTreatmentService dentalPlannedTreatmentService) {
        this.dentalPlannedTreatmentService = dentalPlannedTreatmentService;
    }

    @GetMapping("/by-encounter/{encounterId}")
    public ResponseEntity<DentalTreatmentPlanResponseDTO> getTreatmentPlanForEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get treatment plan for encounterId={}", encounterId);
        return ResponseEntity.ok(dentalPlannedTreatmentService.getTreatmentPlanForEncounter(encounterId));
    }

    @PostMapping("")
    public ResponseEntity<PlannedTreatmentDTO> createPlannedTreatment(@Valid @RequestBody PlannedTreatmentSaveDTO dto) {
        LOG.debug("REST create planned treatment payload={}", dto);
        PlannedTreatmentDTO saved = dentalPlannedTreatmentService.createPlannedTreatment(dto);
        return ResponseEntity
                .created(java.net.URI.create("/api/patient/dental/treatment-plan/" + saved.id()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlannedTreatmentDTO> updatePlannedTreatment(
            @PathVariable Long id,
            @Valid @RequestBody PlannedTreatmentSaveDTO dto
    ) {
        LOG.debug("REST update planned treatment id={} payload={}", id, dto);
        return ResponseEntity.ok(dentalPlannedTreatmentService.updatePlannedTreatment(id, dto));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PlannedTreatmentDTO> cancelPlannedTreatment(
            @PathVariable Long id,
            @Valid @RequestBody PlannedTreatmentCancelDTO dto
    ) {
        LOG.debug("REST cancel planned treatment id={}", id);
        return ResponseEntity.ok(
                dentalPlannedTreatmentService.cancelPlannedTreatment(id, dto.cancellationReason())
        );
    }
}

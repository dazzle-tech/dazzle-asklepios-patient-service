package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DentalPlannedTreatmentService;
import com.dazzle.asklepios.service.DentalTreatmentPlanService;
import com.dazzle.asklepios.service.dto.dentalChart.DentalPlannedTreatmentStatusLogDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanReasonDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanSaveDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanStatusLogDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanSummaryDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentSessionDTO;
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

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DentalTreatmentPlanHeaderController {

    private static final Logger LOG = LoggerFactory.getLogger(DentalTreatmentPlanHeaderController.class);

    private final DentalTreatmentPlanService dentalTreatmentPlanService;
    private final DentalPlannedTreatmentService dentalPlannedTreatmentService;

    public DentalTreatmentPlanHeaderController(
            DentalTreatmentPlanService dentalTreatmentPlanService,
            DentalPlannedTreatmentService dentalPlannedTreatmentService
    ) {
        this.dentalTreatmentPlanService = dentalTreatmentPlanService;
        this.dentalPlannedTreatmentService = dentalPlannedTreatmentService;
    }

    @GetMapping("/dental/treatment-plans/by-patient/{patientId}")
    public ResponseEntity<List<DentalTreatmentPlanSummaryDTO>> listForPatient(@PathVariable Long patientId) {
        LOG.debug("REST list treatment plans for patientId={}", patientId);
        return ResponseEntity.ok(dentalTreatmentPlanService.listForPatient(patientId));
    }

    @GetMapping("/dental/treatment-plans/{id}")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(dentalTreatmentPlanService.get(id));
    }

    @PostMapping("/dental/treatment-plans")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> create(@Valid @RequestBody DentalTreatmentPlanSaveDTO dto) {
        DentalTreatmentPlanSummaryDTO created = dentalTreatmentPlanService.create(dto);
        return ResponseEntity
                .created(URI.create("/api/patient/dental/treatment-plans/" + created.id()))
                .body(created);
    }

    @PutMapping("/dental/treatment-plans/{id}")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> updateHeader(
            @PathVariable Long id,
            @Valid @RequestBody DentalTreatmentPlanSaveDTO dto
    ) {
        return ResponseEntity.ok(dentalTreatmentPlanService.updateHeader(id, dto));
    }

    @GetMapping("/dental/treatment-plans/{id}/sessions")
    public ResponseEntity<List<DentalTreatmentSessionDTO>> sessions(@PathVariable Long id) {
        return ResponseEntity.ok(dentalTreatmentPlanService.listSessions(id));
    }

    @GetMapping("/dental/treatment-plans/{id}/status-log")
    public ResponseEntity<List<DentalTreatmentPlanStatusLogDTO>> statusLog(@PathVariable Long id) {
        return ResponseEntity.ok(dentalTreatmentPlanService.statusLog(id));
    }

    @GetMapping("/dental/treatment-plans/{id}/treatment-history")
    public ResponseEntity<List<DentalPlannedTreatmentStatusLogDTO>> treatmentHistory(@PathVariable Long id) {
        return ResponseEntity.ok(dentalPlannedTreatmentService.listHistoryForPlan(id));
    }

    @PostMapping("/dental/treatment-plans/{id}/propose")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> propose(@PathVariable Long id) {
        return ResponseEntity.ok(dentalTreatmentPlanService.propose(id));
    }

    @PostMapping("/dental/treatment-plans/{id}/accept")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> accept(@PathVariable Long id) {
        return ResponseEntity.ok(dentalTreatmentPlanService.accept(id));
    }

    @PostMapping("/dental/treatment-plans/{id}/return-to-draft")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> returnToDraft(
            @PathVariable Long id,
            @Valid @RequestBody DentalTreatmentPlanReasonDTO dto
    ) {
        return ResponseEntity.ok(dentalTreatmentPlanService.returnToDraft(id, dto));
    }

    @PostMapping("/dental/treatment-plans/{id}/hold")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> hold(
            @PathVariable Long id,
            @Valid @RequestBody DentalTreatmentPlanReasonDTO dto
    ) {
        return ResponseEntity.ok(dentalTreatmentPlanService.hold(id, dto));
    }

    @PostMapping("/dental/treatment-plans/{id}/resume")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> resume(@PathVariable Long id) {
        return ResponseEntity.ok(dentalTreatmentPlanService.resume(id));
    }

    @PostMapping("/dental/treatment-plans/{id}/cancel")
    public ResponseEntity<DentalTreatmentPlanSummaryDTO> cancel(
            @PathVariable Long id,
            @Valid @RequestBody DentalTreatmentPlanReasonDTO dto
    ) {
        return ResponseEntity.ok(dentalTreatmentPlanService.cancel(id, dto));
    }
}

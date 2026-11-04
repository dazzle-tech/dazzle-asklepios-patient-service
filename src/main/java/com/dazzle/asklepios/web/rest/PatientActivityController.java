package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.PatientActivityComment;
import com.dazzle.asklepios.domain.PatientActivityLog;
import com.dazzle.asklepios.domain.enumeration.PatientActivitySource;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.service.PatientActivityService;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityApplyPlanDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityAssignDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityBulkDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCancelDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityChecklistDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCommentDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCompleteDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCreateDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityScheduleDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivitySummaryVM;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/patient")
public class PatientActivityController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientActivityController.class);

    private final PatientActivityService patientActivityService;

    public PatientActivityController(PatientActivityService patientActivityService) {
        this.patientActivityService = patientActivityService;
    }

    @PostMapping("/activities")
    public ResponseEntity<PatientActivity> create(@Valid @RequestBody PatientActivityCreateDTO dto) {
        LOG.debug("REST create PatientActivity payload={}", dto);
        return ResponseEntity.ok(patientActivityService.create(dto));
    }

    @PostMapping("/activities/apply-plan")
    public ResponseEntity<List<PatientActivity>> applyPlan(@Valid @RequestBody PatientActivityApplyPlanDTO dto) {
        LOG.debug("REST apply plan={} patientId={}", dto.getPlanCode(), dto.getPatientId());
        return ResponseEntity.ok(patientActivityService.applyPlan(dto));
    }

    @PutMapping("/activities/{id}")
    public ResponseEntity<PatientActivity> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientActivityUpdateDTO dto
    ) {
        LOG.debug("REST update PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.update(id, dto));
    }

    @PatchMapping("/activities/{id}/complete")
    public ResponseEntity<PatientActivity> complete(
            @PathVariable Long id,
            @Valid @RequestBody PatientActivityCompleteDTO dto
    ) {
        LOG.debug("REST complete PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.complete(id, dto));
    }

    @PatchMapping("/activities/{id}/cancel")
    public ResponseEntity<PatientActivity> cancel(
            @PathVariable Long id,
            @Valid @RequestBody PatientActivityCancelDTO dto
    ) {
        LOG.debug("REST cancel PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.cancel(id, dto));
    }

    @PatchMapping("/activities/{id}/assign")
    public ResponseEntity<PatientActivity> assign(
            @PathVariable Long id,
            @Valid @RequestBody PatientActivityAssignDTO dto
    ) {
        LOG.debug("REST assign PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.assign(id, dto));
    }

    @PatchMapping("/activities/{id}/claim")
    public ResponseEntity<PatientActivity> claim(@PathVariable Long id) {
        LOG.debug("REST claim PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.claim(id));
    }

    @PatchMapping("/activities/{id}/checklist")
    public ResponseEntity<PatientActivity> markChecklistItem(
            @PathVariable Long id,
            @Valid @RequestBody PatientActivityChecklistDTO dto
    ) {
        LOG.debug("REST checklist PatientActivity id={} index={}", id, dto.getIndex());
        return ResponseEntity.ok(patientActivityService.markChecklistItem(id, dto));
    }

    @PatchMapping("/activities/{id}/schedule")
    public ResponseEntity<PatientActivity> reschedule(
            @PathVariable Long id,
            @Valid @RequestBody PatientActivityScheduleDTO dto
    ) {
        LOG.debug("REST reschedule PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.reschedule(id, dto));
    }

    @PostMapping("/activities/bulk")
    public ResponseEntity<Integer> bulkUpdate(@Valid @RequestBody PatientActivityBulkDTO dto) {
        LOG.debug("REST bulk update PatientActivity ids={}", dto.getIds());
        return ResponseEntity.ok(patientActivityService.bulkUpdate(dto));
    }

    @GetMapping("/activities/{id}/history")
    public ResponseEntity<List<PatientActivityLog>> history(@PathVariable Long id) {
        LOG.debug("REST history PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.history(id));
    }

    @GetMapping("/activities/{id}/comments")
    public ResponseEntity<List<PatientActivityComment>> comments(@PathVariable Long id) {
        LOG.debug("REST comments PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.comments(id));
    }

    @PostMapping("/activities/{id}/comments")
    public ResponseEntity<PatientActivityComment> addComment(
            @PathVariable Long id,
            @Valid @RequestBody PatientActivityCommentDTO dto
    ) {
        LOG.debug("REST add comment PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.addComment(id, dto));
    }

    @GetMapping("/activities/{id}")
    public ResponseEntity<PatientActivity> findById(@PathVariable Long id) {
        LOG.debug("REST get PatientActivity id={}", id);
        return ResponseEntity.ok(patientActivityService.findById(id));
    }

    @GetMapping("/activities/patient/{patientId}/summary")
    public ResponseEntity<PatientActivitySummaryVM> summarize(@PathVariable Long patientId) {
        LOG.debug("REST summarize PatientActivity patientId={}", patientId);
        return ResponseEntity.ok(patientActivityService.summarize(patientId));
    }

    @GetMapping("/activities/patient/{patientId}")
    public ResponseEntity<List<PatientActivity>> findByPatient(
            @PathVariable Long patientId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<PatientActivityStatus> status,
            @RequestParam(required = false) PatientActivitySource source,
            @RequestParam(required = false) String search,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientActivity patientId={} category={} status={} source={} search={}", patientId, category, status, source, search);

        Page<PatientActivity> page = patientActivityService.findByPatient(patientId, category, status, source, search, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}

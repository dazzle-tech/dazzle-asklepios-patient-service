package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.enumeration.PatientActivityCategory;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.service.PatientActivityService;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCancelDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCompleteDTO;
import com.dazzle.asklepios.service.dto.patientActivity.PatientActivityCreateDTO;
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
            @RequestParam(required = false) PatientActivityCategory category,
            @RequestParam(required = false) PatientActivityStatus status,
            @RequestParam(required = false) String search,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientActivity patientId={} category={} status={} search={}", patientId, category, status, search);

        Page<PatientActivity> page = patientActivityService.findByPatient(patientId, category, status, search, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}

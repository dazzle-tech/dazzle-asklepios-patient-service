package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.enumeration.TaskScope;
import com.dazzle.asklepios.service.PatientTaskService;
import com.dazzle.asklepios.service.dto.patientActivity.PatientTaskFilter;
import com.dazzle.asklepios.service.dto.patientActivity.PatientTaskSummaryVM;
import com.dazzle.asklepios.service.dto.patientActivity.PatientTaskVM;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientTaskController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientTaskController.class);

    private final PatientTaskService patientTaskService;

    public PatientTaskController(PatientTaskService patientTaskService) {
        this.patientTaskService = patientTaskService;
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<PatientTaskVM>> search(
            @ParameterObject PatientTaskFilter filter,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST search tasks scope={}", filter.getScope());
        Page<PatientTaskVM> page = patientTaskService.search(filter, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/tasks/summary")
    public ResponseEntity<PatientTaskSummaryVM> summarize(@RequestParam(defaultValue = "MINE") TaskScope scope) {
        LOG.debug("REST summarize tasks scope={}", scope);
        return ResponseEntity.ok(patientTaskService.summarize(scope));
    }
}

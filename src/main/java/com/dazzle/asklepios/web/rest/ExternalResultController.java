package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ExternalLabResult;
import com.dazzle.asklepios.domain.ExternalRadiologyReport;
import com.dazzle.asklepios.service.ExternalResultService;
import com.dazzle.asklepios.service.dto.externalresult.ExternalLabResultSaveDTO;
import com.dazzle.asklepios.service.dto.externalresult.ExternalRadiologyReportSaveDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class ExternalResultController {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalResultController.class);

    private final ExternalResultService service;

    @PostMapping(value = "/external-results/lab", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ExternalLabResult>> saveLabResults(
            @Valid @RequestPart("data") ExternalLabResultSaveDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        LOG.debug("REST request to save external lab results patientId={} encounterId={}", dto.patientId(), dto.encounterId());
        return ResponseEntity.ok(service.saveLabResults(dto, file));
    }

    @PostMapping(value = "/external-results/radiology", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ExternalRadiologyReport>> saveRadiologyReports(
            @Valid @RequestPart("data") ExternalRadiologyReportSaveDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        LOG.debug("REST request to save external radiology reports patientId={} encounterId={}", dto.patientId(), dto.encounterId());
        return ResponseEntity.ok(service.saveRadiologyReports(dto, file));
    }

    @GetMapping("/external-results/lab/by-patient/{patientId}")
    public ResponseEntity<List<ExternalLabResult>> getLabResultsByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(service.getLabResultsByPatient(patientId));
    }

    @GetMapping("/external-results/lab/by-encounter/{encounterId}")
    public ResponseEntity<List<ExternalLabResult>> getLabResultsByEncounter(@PathVariable Long encounterId) {
        return ResponseEntity.ok(service.getLabResultsByEncounter(encounterId));
    }

    @GetMapping("/external-results/radiology/by-patient/{patientId}")
    public ResponseEntity<List<ExternalRadiologyReport>> getRadiologyReportsByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(service.getRadiologyReportsByPatient(patientId));
    }

    @GetMapping("/external-results/radiology/by-encounter/{encounterId}")
    public ResponseEntity<List<ExternalRadiologyReport>> getRadiologyReportsByEncounter(@PathVariable Long encounterId) {
        return ResponseEntity.ok(service.getRadiologyReportsByEncounter(encounterId));
    }
}

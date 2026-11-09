package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientFamilyEducation;
import com.dazzle.asklepios.service.PatientFamilyEducationService;
import com.dazzle.asklepios.service.dto.patientFamilyEducation.PatientFamilyEducationSaveDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientFamilyEducationController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientFamilyEducationController.class);

    private final PatientFamilyEducationService patientFamilyEducationService;

    public PatientFamilyEducationController(PatientFamilyEducationService patientFamilyEducationService) {
        this.patientFamilyEducationService = patientFamilyEducationService;
    }

    @PutMapping("/patient-family-educations")
    public ResponseEntity<PatientFamilyEducation> save(@Valid @RequestBody PatientFamilyEducationSaveDTO dto) {
        LOG.debug("REST save PatientFamilyEducation payload={}", dto);
        PatientFamilyEducation saved = patientFamilyEducationService.save(dto);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/patient-family-educations/latest-by-encounter/{encounterId}")
    public ResponseEntity<List<PatientFamilyEducation>> getLatestByEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get latest PatientFamilyEducation encounterId={}", encounterId);
        List<PatientFamilyEducation> body = patientFamilyEducationService.findLatestByEncounterId(encounterId)
                .map(List::of)
                .orElseGet(List::of);
        return ResponseEntity.ok(body);
    }
}

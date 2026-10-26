package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PatientSatisfactionSurveyV2Service;
import com.dazzle.asklepios.service.dto.patientSatisfactionSurveyV2.PatientSatisfactionSurveyV2SubmitDTO;
import com.dazzle.asklepios.web.rest.vm.PatientSatisfactionSurveyV2ResponseVM;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@Slf4j
public class PatientSatisfactionSurveyV2Controller {

    private final PatientSatisfactionSurveyV2Service patientSatisfactionSurveyV2Service;

    // Public: the patient fills the survey without signing in
    @PostMapping("/patient-satisfaction-survey/v2/submit")
    public ResponseEntity<PatientSatisfactionSurveyV2ResponseVM> submitSurvey(
            @Valid @RequestBody PatientSatisfactionSurveyV2SubmitDTO dto) {
        return ResponseEntity.ok(
                patientSatisfactionSurveyV2Service.mapToVM(patientSatisfactionSurveyV2Service.submitSurvey(dto))
        );
    }

    @GetMapping("/patient-satisfaction-survey/v2/responses")
    public ResponseEntity<Page<PatientSatisfactionSurveyV2ResponseVM>> getSubmittedSurveys(Pageable pageable) {
        return ResponseEntity.ok(patientSatisfactionSurveyV2Service.getSubmittedSurveys(pageable));
    }
}

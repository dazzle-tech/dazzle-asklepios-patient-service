package com.dazzle.asklepios.web.rest;


import com.dazzle.asklepios.domain.PatientSatisfactionSurveyResponse;
import com.dazzle.asklepios.service.PatientSatisfactionSurveyService;
import com.dazzle.asklepios.service.dto.patietnSatisfactionSurveyResponse.PatientSatisfactionSurveySubmitDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientSatisfactionSurveyController {

    private final PatientSatisfactionSurveyService
            patientSatisfactionSurveyService;

    @PostMapping("/patient-satisfaction-survey/submit")
    public ResponseEntity<PatientSatisfactionSurveyResponse> submitSurvey(
            @Valid @RequestBody PatientSatisfactionSurveySubmitDTO dto) {

        PatientSatisfactionSurveyResponse response =
                patientSatisfactionSurveyService.submitSurvey(dto);

        return ResponseEntity.ok(response);
    }
}

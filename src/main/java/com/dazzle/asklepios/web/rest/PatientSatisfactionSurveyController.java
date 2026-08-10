package com.dazzle.asklepios.web.rest;


import com.dazzle.asklepios.domain.PatientSatisfactionSurveyResponse;
import com.dazzle.asklepios.service.PatientSatisfactionSurveyService;
import com.dazzle.asklepios.service.dto.patietnSatisfactionSurveyResponse.PatientSatisfactionSurveySubmitDTO;
import com.dazzle.asklepios.web.rest.vm.PatientSatisfactionSurveyResponseAnswerVM;
import com.dazzle.asklepios.web.rest.vm.PatientSatisfactionSurveyResponseVM;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@Slf4j
public class PatientSatisfactionSurveyController {

    private final PatientSatisfactionSurveyService
            patientSatisfactionSurveyService;

    @PostMapping("/patient-satisfaction-survey/submit")
    public ResponseEntity<PatientSatisfactionSurveyResponseVM> submitSurvey(
            @Valid @RequestBody PatientSatisfactionSurveySubmitDTO dto) {

        PatientSatisfactionSurveyResponse response =
                patientSatisfactionSurveyService.submitSurvey(dto);

        return ResponseEntity.ok(
                mapToVM(response)
        );
    }
    private PatientSatisfactionSurveyResponseVM mapToVM(
            PatientSatisfactionSurveyResponse response) {

        PatientSatisfactionSurveyResponseVM vm =
                new PatientSatisfactionSurveyResponseVM();

        vm.setId(response.getId());
        vm.setPatientName(response.getPatientName());
        vm.setStatus(response.getStatus());
        vm.setStartedAt(response.getStartedAt());
        vm.setCompletedAt(response.getCompletedAt());
        vm.setOverallScore(response.getOverallScore());
        vm.setOverallPercentage(response.getOverallPercentage());
        vm.setCreatedDate(response.getCreatedDate());

        vm.setAnswers(
                response.getAnswers()
                        .stream()
                        .map(answer -> {
                            PatientSatisfactionSurveyResponseAnswerVM answerVM =
                                    new PatientSatisfactionSurveyResponseAnswerVM();

                            answerVM.setId(answer.getId());
                            answerVM.setQuestionCode(answer.getQuestionCode());
                            answerVM.setAnswer(answer.getAnswer());
                            answerVM.setScore(answer.getScore());

                            return answerVM;
                        })
                        .toList()
        );

        return vm;
    }
}

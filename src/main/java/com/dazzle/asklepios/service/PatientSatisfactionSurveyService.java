package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientSatisfactionSurveyResponse;
import com.dazzle.asklepios.domain.PatientSatisfactionSurveyResponseAnswer;
import com.dazzle.asklepios.domain.enumeration.PatientSatisfactionSurveyResponseStatus;
import com.dazzle.asklepios.repository.PatientSatisfactionSurveyResponseRepository;
import com.dazzle.asklepios.service.dto.patietnSatisfactionSurveyResponse.PatientSatisfactionSurveyAnswerDTO;
import com.dazzle.asklepios.service.dto.patietnSatisfactionSurveyResponse.PatientSatisfactionSurveySubmitDTO;
import com.dazzle.asklepios.web.rest.vm.PatientSatisfactionSurveyResponseAnswerVM;
import com.dazzle.asklepios.web.rest.vm.PatientSatisfactionSurveyResponseVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PatientSatisfactionSurveyService {

    private final PatientSatisfactionSurveyResponseRepository responseRepository;

    public PatientSatisfactionSurveyResponse submitSurvey(
            PatientSatisfactionSurveySubmitDTO dto) {

        log.debug(
                "Submitting patient satisfaction survey for patient: {}",
                dto.getPatientName()
        );

        Instant now = Instant.now();

        PatientSatisfactionSurveyResponse response =
                new PatientSatisfactionSurveyResponse();

        response.setPatientName(dto.getPatientName().trim());
        response.setStatus(
                PatientSatisfactionSurveyResponseStatus.COMPLETED
        );
        response.setStartedAt(now);
        response.setCompletedAt(now);
        response.setCreatedDate(now);

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maximumScore = BigDecimal.ZERO;

        for (PatientSatisfactionSurveyAnswerDTO answerDTO : dto.getAnswers()) {

            BigDecimal score = calculateScore(
                    answerDTO.getQuestionCode(),
                    answerDTO.getAnswer()
            );

            PatientSatisfactionSurveyResponseAnswer answer =
                    new PatientSatisfactionSurveyResponseAnswer();

            answer.setResponse(response);
            answer.setQuestionCode(answerDTO.getQuestionCode());
            answer.setAnswer(answerDTO.getAnswer());
            answer.setScore(score);

            response.getAnswers().add(answer);

            totalScore = totalScore.add(score);

            maximumScore = maximumScore.add(
                    BigDecimal.valueOf(5)
            );
        }

        BigDecimal percentage = calculatePercentage(
                totalScore,
                maximumScore
        );

        response.setOverallScore(totalScore);
        response.setOverallPercentage(percentage);

        return responseRepository.save(response);
    }

    @Transactional(readOnly = true)
    public Page<PatientSatisfactionSurveyResponseVM> getSubmittedSurveys(
            Pageable pageable) {

        return responseRepository
                .findAll(pageable)
                .map(this::mapToVM);
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

    private BigDecimal calculateScore(
            String questionCode,
            String answer) {

        if (answer == null || answer.isBlank()) {
            return BigDecimal.ZERO;
        }

        /*
         * Define the scoring according to your survey.
         *
         * Example:
         *
         * VERY_SATISFIED     = 5
         * SATISFIED          = 4
         * NEUTRAL            = 3
         * DISSATISFIED       = 2
         * VERY_DISSATISFIED  = 1
         */

        return switch (answer) {
            case "VERY_SATISFIED" -> BigDecimal.valueOf(5);
            case "SATISFIED" -> BigDecimal.valueOf(4);
            case "NEUTRAL" -> BigDecimal.valueOf(3);
            case "DISSATISFIED" -> BigDecimal.valueOf(2);
            case "VERY_DISSATISFIED" -> BigDecimal.valueOf(1);
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal calculatePercentage(
            BigDecimal totalScore,
            BigDecimal maximumScore) {

        if (maximumScore.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalScore
                .divide(
                        maximumScore,
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
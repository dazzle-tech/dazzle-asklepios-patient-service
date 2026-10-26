package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientSatisfactionSurveyV2Response;
import com.dazzle.asklepios.domain.PatientSatisfactionSurveyV2ResponseAnswer;
import com.dazzle.asklepios.domain.enumeration.PatientSatisfactionSurveyV2Question;
import com.dazzle.asklepios.domain.enumeration.PatientSatisfactionSurveyResponseStatus;
import com.dazzle.asklepios.repository.PatientSatisfactionSurveyV2ResponseRepository;
import com.dazzle.asklepios.service.dto.patientSatisfactionSurveyV2.PatientSatisfactionSurveyV2AnswerDTO;
import com.dazzle.asklepios.service.dto.patientSatisfactionSurveyV2.PatientSatisfactionSurveyV2SubmitDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientSatisfactionSurveyV2ResponseAnswerVM;
import com.dazzle.asklepios.web.rest.vm.PatientSatisfactionSurveyV2ResponseVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PatientSatisfactionSurveyV2Service {

    private static final String ENTITY_NAME = "patientSatisfactionSurveyV2";
    private static final int MAX_SCORE = 5;

    private final PatientSatisfactionSurveyV2ResponseRepository responseRepository;

    public PatientSatisfactionSurveyV2Response submitSurvey(PatientSatisfactionSurveyV2SubmitDTO dto) {
        log.debug("Submitting patient satisfaction survey v2 with {} answers", dto.getAnswers().size());

        Instant now = Instant.now();

        PatientSatisfactionSurveyV2Response response = new PatientSatisfactionSurveyV2Response();
        String patientName = dto.getPatientName() == null ? null : dto.getPatientName().trim();
        response.setPatientName(patientName == null || patientName.isEmpty() ? null : patientName);
        response.setStatus(PatientSatisfactionSurveyResponseStatus.COMPLETED);
        // startedAt comes from the patient's device clock; one ahead of the server is not trusted
        Instant startedAt = dto.getStartedAt();
        response.setStartedAt(startedAt != null && !startedAt.isAfter(now) ? startedAt : now);
        response.setCompletedAt(now);
        response.setCreatedDate(now);

        int totalScore = 0;
        int ratedQuestions = 0;
        Set<PatientSatisfactionSurveyV2Question> answered = new HashSet<>();

        for (PatientSatisfactionSurveyV2AnswerDTO answerDTO : dto.getAnswers()) {
            PatientSatisfactionSurveyV2Question question = toQuestion(answerDTO.getQuestionCode());
            if (!answered.add(question)) {
                throw new BadRequestAlertException(
                        "Question " + question + " is answered twice", ENTITY_NAME, "question.duplicate");
            }

            PatientSatisfactionSurveyV2ResponseAnswer answer = new PatientSatisfactionSurveyV2ResponseAnswer();
            answer.setQuestionCode(question.name());

            if (question.isFreeText()) {
                String text = answerDTO.getAnswerText() == null ? null : answerDTO.getAnswerText().trim();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                answer.setAnswerText(text);
            } else {
                if (answerDTO.getScore() == null) {
                    continue;
                }
                answer.setScore(answerDTO.getScore());
                totalScore += answerDTO.getScore();
                ratedQuestions++;
            }

            response.addAnswer(answer);
        }

        if (response.getAnswers().isEmpty()) {
            throw new BadRequestAlertException("Please answer at least one question", ENTITY_NAME, "answers.empty");
        }

        response.setOverallScore(BigDecimal.valueOf(totalScore));
        response.setOverallPercentage(percentage(totalScore, ratedQuestions * MAX_SCORE));

        return responseRepository.save(response);
    }

    @Transactional(readOnly = true)
    public Page<PatientSatisfactionSurveyV2ResponseVM> getSubmittedSurveys(Pageable pageable) {
        return responseRepository.findAll(pageable).map(this::mapToVM);
    }

    public PatientSatisfactionSurveyV2ResponseVM mapToVM(PatientSatisfactionSurveyV2Response response) {
        PatientSatisfactionSurveyV2ResponseVM vm = new PatientSatisfactionSurveyV2ResponseVM();
        vm.setId(response.getId());
        vm.setPatientName(response.getPatientName());
        vm.setStatus(response.getStatus());
        vm.setStartedAt(response.getStartedAt());
        vm.setCompletedAt(response.getCompletedAt());
        vm.setOverallScore(response.getOverallScore());
        vm.setOverallPercentage(response.getOverallPercentage());
        vm.setCreatedDate(response.getCreatedDate());
        vm.setAnswers(
                response.getAnswers().stream()
                        .map(answer -> {
                            PatientSatisfactionSurveyV2ResponseAnswerVM answerVM = new PatientSatisfactionSurveyV2ResponseAnswerVM();
                            answerVM.setId(answer.getId());
                            answerVM.setQuestionCode(answer.getQuestionCode());
                            answerVM.setScore(answer.getScore());
                            answerVM.setAnswerText(answer.getAnswerText());
                            return answerVM;
                        })
                        .toList()
        );
        return vm;
    }

    private PatientSatisfactionSurveyV2Question toQuestion(String questionCode) {
        try {
            return PatientSatisfactionSurveyV2Question.valueOf(questionCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(
                    "Unknown survey question: " + questionCode, ENTITY_NAME, "question.invalid");
        }
    }

    private BigDecimal percentage(int totalScore, int maximumScore) {
        if (maximumScore == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalScore)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maximumScore), 2, RoundingMode.HALF_UP);
    }
}

package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.SurveyQuestion;
import com.dazzle.asklepios.domain.SurveyResponse;
import com.dazzle.asklepios.domain.SurveyResponseAnswer;
import com.dazzle.asklepios.domain.SurveySection;
import com.dazzle.asklepios.domain.SurveyText;
import com.dazzle.asklepios.domain.enumeration.SurveyAnswerType;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.SurveyQuestionRepository;
import com.dazzle.asklepios.repository.SurveyResponseAnswerRepository;
import com.dazzle.asklepios.repository.SurveyResponseRepository;
import com.dazzle.asklepios.repository.SurveySectionRepository;
import com.dazzle.asklepios.repository.SurveyTextRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.survey.SurveyAnswerDTO;
import com.dazzle.asklepios.service.dto.survey.SurveySubmitDTO;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.survey.SurveyFormVM;
import com.dazzle.asklepios.web.rest.vm.survey.SurveyResponseVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The public patient satisfaction survey: the questions and submitting, and the responses page.
 * A survey is linked to the patient through a token: the app's patient portal login token,
 * or the web page's survey OTP token (SurveyOtpService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SurveyService {

    private static final String ENTITY_NAME = "survey";
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final String PATIENT_ROLE = "ROLE_PATIENT";

    private final SurveyQuestionRepository questionRepository;
    private final SurveySectionRepository sectionRepository;
    private final SurveyTextRepository textRepository;
    private final SurveyResponseRepository responseRepository;
    private final SurveyResponseAnswerRepository answerRepository;
    private final PatientRepository patientRepository;
    private final SurveyOtpService otpService;
    private final NotificationHelper notificationHelper;

    @Transactional(readOnly = true)
    public SurveyFormVM getForm() {
        Map<Long, List<SurveyFormVM.Question>> questionsBySection = activeQuestions()
                .stream()
                .collect(Collectors.groupingBy(
                        SurveyQuestion::getSectionId,
                        Collectors.mapping(this::toQuestionVM, Collectors.toList())
                ));

        // Sections without active questions are not shown
        List<SurveyFormVM.Section> sections = sectionRepository.findByIsActiveTrueOrderByDisplayOrderAscIdAsc()
                .stream()
                .filter(section -> questionsBySection.containsKey(section.getId()))
                .map(section -> new SurveyFormVM.Section(
                        section.getId(),
                        section.getNameEn(),
                        section.getNameAr(),
                        questionsBySection.get(section.getId())
                ))
                .toList();

        Map<String, SurveyFormVM.Text> texts = textRepository.findByIsActiveTrue().stream()
                .collect(Collectors.toMap(
                        SurveyText::getTextKey,
                        text -> new SurveyFormVM.Text(text.getTextEn(), text.getTextAr()),
                        (left, right) -> left
                ));

        return new SurveyFormVM(sections, texts);
    }

    public SurveyResponse submit(SurveySubmitDTO dto) {
        log.debug("Submitting patient satisfaction survey with {} answers", dto.answers().size());

        Instant now = Instant.now();
        SurveyResponse response = new SurveyResponse();

        // The patient comes from a token, never from a patient id sent in the body:
        // the app's login token first, then the web page's OTP token
        Long patientId = currentPatientId();
        if (patientId == null && dto.verificationToken() != null && !dto.verificationToken().isBlank()) {
            patientId = otpService.patientIdFromToken(dto.verificationToken());
        }
        response.setPatientId(patientId);

        // startedAt comes from the patient's device clock; one ahead of the server is not trusted
        Instant startedAt = dto.startedAt();
        response.setStartedAt(startedAt != null && !startedAt.isAfter(now) ? startedAt : now);
        response.setCompletedAt(now);

        Map<Long, SurveyQuestion> activeQuestions = activeQuestions()
                .stream()
                .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity()));

        int totalScore = 0;
        int maximumScore = 0;
        Set<Long> answered = new HashSet<>();

        for (SurveyAnswerDTO answerDTO : dto.answers()) {
            SurveyQuestion question = activeQuestions.get(answerDTO.questionId());
            if (question == null) {
                throw new BadRequestAlertException(
                        "Unknown or inactive survey question: " + answerDTO.questionId(), ENTITY_NAME, "question.invalid");
            }
            if (answered.contains(question.getId())) {
                throw new BadRequestAlertException(
                        "Question " + question.getId() + " is answered twice", ENTITY_NAME, "question.duplicate");
            }

            SurveyResponseAnswer answer = new SurveyResponseAnswer();
            answer.setQuestionId(question.getId());

            if (question.getAnswerType() == SurveyAnswerType.TEXT) {
                String text = answerDTO.textValue() == null ? null : answerDTO.textValue().trim();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                if (text.length() > MAX_TEXT_LENGTH) {
                    throw new BadRequestAlertException(
                            "Answers can be at most " + MAX_TEXT_LENGTH + " characters", ENTITY_NAME, "text.tooLong");
                }
                answer.setTextValue(text);
            } else {
                Integer value = answerDTO.score();
                if (value == null) {
                    continue;
                }
                if (value < question.getMinValue() || value > question.getMaxValue()) {
                    throw new BadRequestAlertException(
                            "Answer of question " + question.getId() + " is out of its scale", ENTITY_NAME, "scale.outOfRange");
                }
                answer.setScore(value);
                totalScore += value;
                maximumScore += question.getMaxValue();
            }

            answered.add(question.getId());
            response.addAnswer(answer);
        }

        boolean missingRequired = activeQuestions.values().stream()
                .anyMatch(question -> Boolean.TRUE.equals(question.getIsRequired()) && !answered.contains(question.getId()));
        if (missingRequired) {
            throw new BadRequestAlertException("Please answer all required questions", ENTITY_NAME, "answers.requiredMissing");
        }
        if (response.getAnswers().isEmpty()) {
            throw new BadRequestAlertException("Please answer at least one question", ENTITY_NAME, "answers.empty");
        }

        response.setOverallScore(BigDecimal.valueOf(totalScore));
        response.setOverallPercentage(percentage(totalScore, maximumScore));

        return responseRepository.save(response);
    }

    /** Submitted surveys for the responses page, with the patient and the questions resolved for display */
    @Transactional(readOnly = true)
    public Page<SurveyResponseVM> getResponses(Pageable pageable) {
        Page<SurveyResponse> page = responseRepository.findAll(pageable);

        Set<Long> patientIds = page.getContent().stream()
                .map(SurveyResponse::getPatientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Patient> patientsById = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));

        // Already in the order the patient saw them, with their question and section (inactive ones too)
        Map<Long, List<SurveyResponseVM.Answer>> answersByResponse = answerRepository
                .findByResponseIdInOrderByQuestion_Section_DisplayOrderAscQuestion_Section_IdAscQuestion_DisplayOrderAscQuestion_IdAsc(
                        page.getContent().stream().map(SurveyResponse::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        answer -> answer.getResponse().getId(),
                        Collectors.mapping(SurveyService::toAnswerVM, Collectors.toList())
                ));

        return page.map(response -> {
            Patient patient = response.getPatientId() == null ? null : patientsById.get(response.getPatientId());
            return new SurveyResponseVM(
                    response.getId(),
                    response.getPatientId(),
                    patient == null ? null : blankToNull(notificationHelper.getPatientName(patient)),
                    patient == null ? null : patient.getMedicalRecordNumber(),
                    response.getOverallScore(),
                    response.getOverallPercentage(),
                    response.getStartedAt(),
                    response.getCompletedAt(),
                    response.getCreatedDate(),
                    answersByResponse.getOrDefault(response.getId(), List.of())
            );
        });
    }

    private static SurveyResponseVM.Answer toAnswerVM(SurveyResponseAnswer answer) {
        SurveyQuestion question = answer.getQuestion();
        SurveySection section = question.getSection();
        return new SurveyResponseVM.Answer(
                answer.getId(),
                answer.getQuestionId(),
                section.getNameEn(),
                section.getNameAr(),
                question.getQuestionEn(),
                question.getQuestionAr(),
                question.getAnswerType(),
                question.getMinValue(),
                question.getMaxValue(),
                answer.getScore(),
                answer.getTextValue()
        );
    }

    /**
     * The patient who sent the request, from the patient portal login token:
     * subject = medical record number, "auth" claim has ROLE_PATIENT.
     * Null without a token or with a staff token, so the survey is saved without a patient.
     */
    private Long currentPatientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String authorities = jwt.getClaimAsString(SecurityUtils.AUTHORITIES_CLAIM);
        if (authorities == null || !Arrays.asList(authorities.split(" ")).contains(PATIENT_ROLE)) {
            return null;
        }
        return patientRepository.findByMedicalRecordNumber(jwt.getSubject())
                .map(Patient::getId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient of this login was not found", ENTITY_NAME, "patient.notfound"));
    }

    /** Active questions of active sections, in the order the patient sees them */
    private List<SurveyQuestion> activeQuestions() {
        return questionRepository.findByIsActiveTrueAndSection_IsActiveTrueOrderBySection_DisplayOrderAscSection_IdAscDisplayOrderAscIdAsc();
    }

    private SurveyFormVM.Question toQuestionVM(SurveyQuestion question) {
        return new SurveyFormVM.Question(
                question.getId(),
                question.getQuestionEn(),
                question.getQuestionAr(),
                question.getAnswerType(),
                question.getMinValue(),
                question.getMaxValue(),
                Boolean.TRUE.equals(question.getIsRequired())
        );
    }

    private static BigDecimal percentage(int totalScore, int maximumScore) {
        if (maximumScore == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalScore)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maximumScore), 2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

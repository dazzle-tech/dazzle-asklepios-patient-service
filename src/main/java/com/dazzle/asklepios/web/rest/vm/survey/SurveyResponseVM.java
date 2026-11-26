package com.dazzle.asklepios.web.rest.vm.survey;

import com.dazzle.asklepios.domain.enumeration.SurveyAnswerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A submitted survey for the responses page. The patient fields are empty for anonymous answers */
public record SurveyResponseVM(
        Long id,
        Long patientId,
        String patientName,
        String medicalRecordNumber,
        BigDecimal overallScore,
        BigDecimal overallPercentage,
        Instant startedAt,
        Instant completedAt,
        Instant createdDate,
        List<Answer> answers
) {

    /** The answer with its question in both languages, since the survey language is not stored */
    public record Answer(
            Long id,
            Long questionId,
            String sectionNameEn,
            String sectionNameAr,
            String questionEn,
            String questionAr,
            SurveyAnswerType answerType,
            Integer minValue,
            Integer maxValue,
            Integer score,
            String textValue
    ) {
    }
}

package com.dazzle.asklepios.web.rest.vm.survey;

import com.dazzle.asklepios.domain.enumeration.SurveyAnswerType;

import java.util.List;
import java.util.Map;

/**
 * The survey the patient fills: active sections, each with its active questions, in order,
 * and the active page texts by key (the page keeps its own default for a missing key).
 */
public record SurveyFormVM(List<Section> sections, Map<String, Text> texts) {

    public record Section(Long id, String nameEn, String nameAr, List<Question> questions) {
    }

    public record Question(
            Long id,
            String questionEn,
            String questionAr,
            SurveyAnswerType answerType,
            Integer minValue,
            Integer maxValue,
            Boolean isRequired
    ) {
    }

    public record Text(String en, String ar) {
    }
}

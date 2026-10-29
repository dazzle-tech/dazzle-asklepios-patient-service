package com.dazzle.asklepios.service.dto.survey;

import jakarta.validation.constraints.NotNull;

/** score answers a SCALE question, textValue answers a TEXT question */
public record SurveyAnswerDTO(
        @NotNull Long questionId,
        Integer score,
        String textValue
) {
}

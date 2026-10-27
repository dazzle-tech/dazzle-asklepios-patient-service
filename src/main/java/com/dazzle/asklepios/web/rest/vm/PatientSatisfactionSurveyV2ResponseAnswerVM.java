package com.dazzle.asklepios.web.rest.vm;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientSatisfactionSurveyV2ResponseAnswerVM {
    private Long id;
    private String questionCode;
    private Integer score;
    private String answerText;
}

package com.dazzle.asklepios.web.rest.vm;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PatientSatisfactionSurveyResponseAnswerVM {

    private Long id;
    private String questionCode;
    private String answer;
    private BigDecimal score;
}

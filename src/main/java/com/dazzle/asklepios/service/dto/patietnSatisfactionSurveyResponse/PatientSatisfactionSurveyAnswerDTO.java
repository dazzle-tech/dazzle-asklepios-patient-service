package com.dazzle.asklepios.service.dto.patietnSatisfactionSurveyResponse;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PatientSatisfactionSurveyAnswerDTO {

    @NotBlank
    private String questionCode;

    private String answer;
}
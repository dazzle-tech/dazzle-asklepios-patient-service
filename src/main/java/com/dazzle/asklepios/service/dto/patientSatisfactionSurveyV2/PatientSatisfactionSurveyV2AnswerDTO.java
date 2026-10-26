package com.dazzle.asklepios.service.dto.patientSatisfactionSurveyV2;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientSatisfactionSurveyV2AnswerDTO {

    @NotBlank
    private String questionCode;

    @Min(1)
    @Max(5)
    private Integer score;

    @Size(max = 2000)
    private String answerText;
}

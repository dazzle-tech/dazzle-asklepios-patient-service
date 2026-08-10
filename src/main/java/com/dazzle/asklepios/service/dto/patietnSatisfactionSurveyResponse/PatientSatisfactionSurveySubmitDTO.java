package com.dazzle.asklepios.service.dto.patietnSatisfactionSurveyResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientSatisfactionSurveySubmitDTO {

    @NotBlank
    private String patientName;

    @NotEmpty
    @Valid
    private List<PatientSatisfactionSurveyAnswerDTO> answers;
}
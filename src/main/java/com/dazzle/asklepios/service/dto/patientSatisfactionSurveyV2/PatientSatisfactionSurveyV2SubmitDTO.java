package com.dazzle.asklepios.service.dto.patientSatisfactionSurveyV2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class PatientSatisfactionSurveyV2SubmitDTO {

    // Optional: the patient may answer without a name
    @Size(max = 255)
    private String patientName;

    private Instant startedAt;

    @NotEmpty
    @Valid
    private List<PatientSatisfactionSurveyV2AnswerDTO> answers;
}

package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.enumeration.PatientSatisfactionSurveyResponseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class PatientSatisfactionSurveyResponseVM {

    private Long id;
    private String patientName;
    private PatientSatisfactionSurveyResponseStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private BigDecimal overallScore;
    private BigDecimal overallPercentage;
    private Instant createdDate;
    private List<PatientSatisfactionSurveyResponseAnswerVM> answers;
}

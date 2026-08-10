package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientSatisfactionSurveyResponseStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patient_satisfaction_survey_response")
@Getter
@Setter
public class PatientSatisfactionSurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_name", nullable = false)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PatientSatisfactionSurveyResponseStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "overall_score", precision = 10, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "overall_percentage", precision = 5, scale = 2)
    private BigDecimal overallPercentage;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @OneToMany(
            mappedBy = "response",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PatientSatisfactionSurveyResponseAnswer> answers =
            new ArrayList<>();

    public void addAnswer(PatientSatisfactionSurveyResponseAnswer answer) {
        answers.add(answer);
        answer.setResponse(this);
    }

    public void removeAnswer(PatientSatisfactionSurveyResponseAnswer answer) {
        answers.remove(answer);
        answer.setResponse(null);
    }
}

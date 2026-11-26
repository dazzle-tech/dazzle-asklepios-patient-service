package com.dazzle.asklepios.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** One submitted patient satisfaction survey. patientId is empty when the patient answered anonymously */
@Entity
@Table(name = "survey_response")
@Getter
@Setter
public class SurveyResponse extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    /** Sum of the scale answers */
    @Column(name = "overall_score", precision = 10, scale = 2)
    private BigDecimal overallScore;

    /** overallScore out of the max value of every answered scale question */
    @Column(name = "overall_percentage", precision = 5, scale = 2)
    private BigDecimal overallPercentage;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SurveyResponseAnswer> answers = new ArrayList<>();

    public void addAnswer(SurveyResponseAnswer answer) {
        answers.add(answer);
        answer.setResponse(this);
    }
}

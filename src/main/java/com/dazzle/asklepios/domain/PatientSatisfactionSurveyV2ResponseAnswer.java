package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "patient_satisfaction_survey_v2_response_answer")
@Getter
@Setter
public class PatientSatisfactionSurveyV2ResponseAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private PatientSatisfactionSurveyV2Response response;

    @Column(name = "question_code", nullable = false, length = 100)
    private String questionCode;

    @Column(name = "score")
    private Integer score;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;
}

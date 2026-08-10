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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "patient_satisfaction_survey_response_answer")
@Getter
@Setter
public class PatientSatisfactionSurveyResponseAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private PatientSatisfactionSurveyResponse response;

    @Column(name = "question_code", nullable = false, length = 100)
    private String questionCode;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "score", precision = 10, scale = 2)
    private BigDecimal score;
}
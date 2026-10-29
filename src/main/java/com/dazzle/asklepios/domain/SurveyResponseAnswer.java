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

/** The answer to one question: score for SCALE questions, textValue for TEXT questions */
@Entity
@Table(name = "survey_response_answer")
@Getter
@Setter
public class SurveyResponseAnswer extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private SurveyResponse response;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    // Same column as questionId, read only: lets the repository sort answers by section and question
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private SurveyQuestion question;

    @Column(name = "score")
    private Integer score;

    @Column(name = "text_value", columnDefinition = "TEXT")
    private String textValue;
}

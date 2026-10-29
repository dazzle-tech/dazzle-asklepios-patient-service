package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.SurveyAnswerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of the setup-owned {@code survey_question} table.
 * Its text and answer limits never change, so answers only keep the question id.
 */
@Entity
@Immutable
@Table(name = "survey_question")
@Getter
@NoArgsConstructor
public class SurveyQuestion {

    @Id
    private Long id;

    @Column(name = "section_id")
    private Long sectionId;

    // Same column as sectionId, read only: lets the repository filter and sort by the section
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", insertable = false, updatable = false)
    private SurveySection section;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "question_en")
    private String questionEn;

    @Column(name = "question_ar")
    private String questionAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type")
    private SurveyAnswerType answerType;

    @Column(name = "min_value")
    private Integer minValue;

    @Column(name = "max_value")
    private Integer maxValue;

    @Column(name = "is_required")
    private Boolean isRequired;

    @Column(name = "is_active")
    private Boolean isActive;
}

package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Read-only view of the setup-owned {@code survey_text} table: texts of the survey page */
@Entity
@Immutable
@Table(name = "survey_text")
@Getter
@NoArgsConstructor
public class SurveyText {

    @Id
    private Long id;

    @Column(name = "text_key", nullable = false, length = 100, unique = true)
    private String textKey;

    @Column(name = "text_en", nullable = false, columnDefinition = "TEXT")
    private String textEn;

    @Column(name = "text_ar", nullable = false, columnDefinition = "TEXT")
    private String textAr;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

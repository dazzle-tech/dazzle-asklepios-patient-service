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

    @Column(name = "text_key")
    private String textKey;

    @Column(name = "text_en")
    private String textEn;

    @Column(name = "text_ar")
    private String textAr;

    @Column(name = "is_active")
    private Boolean isActive;
}

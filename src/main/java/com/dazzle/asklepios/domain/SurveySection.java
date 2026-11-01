package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Read-only view of the setup-owned {@code survey_section} table */
@Entity
@Immutable
@Table(name = "survey_section")
@Getter
@NoArgsConstructor
public class SurveySection {

    @Id
    private Long id;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", nullable = false, length = 255)
    private String nameAr;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

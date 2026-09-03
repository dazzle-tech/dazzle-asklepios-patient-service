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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "patient_encounter_assessment_field_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncounterAssessmentLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_encounter_assessment_id", nullable = false)
    private EncounterAssessment encounterAssessment;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "operation_type", nullable = false, length = 10)
    private String operationType;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "log_date", nullable = false)
    private Instant logDate;

    @Column(name = "log_by", length = 50)
    private String logBy;
}
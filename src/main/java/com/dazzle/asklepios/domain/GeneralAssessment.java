package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "general_assessment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralAssessment extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "position_status", length = 50, nullable = false)
    private String positionStatus;

    @Column(name = "body_movements", length = 50, nullable = false)
    private String bodyMovements;

    @Column(name = "level_of_consciousness", length = 50, nullable = false)
    private String levelOfConsciousness;

    @Column(name = "facial_expression", length = 50, nullable = false)
    private String facialExpression;

    @Column(name = "speech", length = 50, nullable = false)
    private String speech;

    @Column(name = "mood_behavior", length = 50, nullable = false)
    private String moodBehavior;

    @Column(name = "memory_remote", nullable = false)
    private Boolean memoryRemote = false;

    @Column(name = "memory_recent", nullable = false)
    private Boolean memoryRecent = false;

    @Column(name = "signs_of_agitation", nullable = false)
    private Boolean signsOfAgitation = false;

    @Column(name = "signs_of_depression", nullable = false)
    private Boolean signsOfDepression = false;

    @Column(name = "signs_of_suicidal_ideation", nullable = false)
    private Boolean signsOfSuicidalIdeation = false;

    @Column(name = "signs_of_substance_use", nullable = false)
    private Boolean signsOfSubstanceUse = false;

    @Column(name = "is_triage", nullable = false)
    private Boolean isTriage = false;
}
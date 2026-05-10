package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.GCSEye;
import com.dazzle.asklepios.domain.enumeration.GCSMotor;
import com.dazzle.asklepios.domain.enumeration.GCSVerbal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "glasgow_coma_scale_assessment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlasgowComaScaleAssessment extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "eye_opening", nullable = false, length = 100)
    private GCSEye eyeOpening;

    @NotNull
    @Column(name = "eye_opening_score", nullable = false)
    private Integer eyeOpeningScore;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "verbal_response", nullable = false, length = 100)
    private GCSVerbal verbalResponse;

    @NotNull
    @Column(name = "verbal_response_score", nullable = false)
    private Integer verbalResponseScore;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "motor_response", nullable = false, length = 100)
    private GCSMotor motorResponse;

    @NotNull
    @Column(name = "motor_response_score", nullable = false)
    private Integer motorResponseScore;

    @NotNull
    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @NotNull
    @Column(name = "score_interpretation", nullable = false, length = 255)
    private String scoreInterpretation;

}
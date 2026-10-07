package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.CkdStage;
import com.dazzle.asklepios.domain.enumeration.KidneyCondition;
import com.dazzle.asklepios.domain.enumeration.KidneyDiseaseCause;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "nephrology_kidney_assessment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyKidneyAssessment
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ckd_stage")
    private CkdStage ckdStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "kidney_condition")
    private KidneyCondition kidneyCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "cause_of_kidney_disease")
    private KidneyDiseaseCause causeOfKidneyDisease;

    @Column(name = "other_cause_of_kidney_disease", length = 500)
    private String otherCauseOfKidneyDisease;

    @Column(name = "diabetes")
    private Boolean diabetes;

    @Column(name = "hypertension")
    private Boolean hypertension;

    @Column(name = "proteinuria")
    private Boolean proteinuria;

    @Column(name = "hematuria")
    private Boolean hematuria;

    @NotNull
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Override
    public Long getId() {
        return id;
    }
}
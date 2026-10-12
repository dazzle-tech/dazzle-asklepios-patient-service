package com.dazzle.asklepios.domain;


import com.dazzle.asklepios.domain.enumeration.DiagnosisType;
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
@Table(name = "claim_encounter_diagnoses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimEncounterDiagnosis extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "claim_encounter_copy_id", nullable = false)
    private Long claimEncounterCopyId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Column(name = "diagnosis_id", nullable = false)
    private Long diagnosisId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private DiagnosisType type;

    @NotNull
    @Column(name = "suspected", nullable = false)
    @Builder.Default
    private Boolean suspected = false;

    @NotNull
    @Column(name = "major", nullable = false)
    @Builder.Default
    private Boolean major = false;
}
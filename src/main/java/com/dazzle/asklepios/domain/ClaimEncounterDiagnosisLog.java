package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "claim_encounter_diagnosis_logs")
@Getter
@Setter
public class ClaimEncounterDiagnosisLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_encounter_diagnosis_id")
    private Long claimEncounterDiagnosisId;
}
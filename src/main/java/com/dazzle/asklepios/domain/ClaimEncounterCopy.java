package com.dazzle.asklepios.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "claim_encounter_copies")
@Getter
@Setter
public class ClaimEncounterCopy extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    @Column(name = "history_of_present_illness")
    private String historyOfPresentIllness;

    @Column(name = "physical_examination")
    private String physicalExamination;

    @Column(name = "assessment")
    private String assessment;

    @Column(name = "treatment_plan")
    private String treatmentPlan;

    @Column(name = "pulse")
    private Integer pulse;

    @Column(name = "temperature")
    private BigDecimal temperature;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "oxygen_saturation")
    private BigDecimal oxygenSaturation;

    @Column(name = "blood_pressure_systolic")
    private Integer bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private Integer bloodPressureDiastolic;

    @Column(name = "height")
    private BigDecimal height;

    @Column(name = "weight")
    private BigDecimal weight;


}
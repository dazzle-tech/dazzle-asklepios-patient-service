package com.dazzle.asklepios.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patient_procedure_post_care")
@Getter
@Setter
@NoArgsConstructor
public class PatientProcedurePostCare extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_procedure_id", nullable = false)
    @JsonIgnore
    private PatientProcedure patientProcedure;

    @Column(name = "diagnosis_icd_id")
    private Long diagnosisIcdId;

    @Column(name = "diagnosis_description", length = 500)
    private String diagnosisDescription;

    @Column(name = "patient_condition", columnDefinition = "text")
    private String patientCondition;

    @Column(name = "blood_pressure_systolic")
    private BigDecimal bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private BigDecimal bloodPressureDiastolic;

    @Column(name = "heart_rate")
    private BigDecimal heartRate;

    @Column(name = "temperature")
    private BigDecimal temperature;

    @Column(name = "oxygen_saturation")
    private BigDecimal oxygenSaturation;

    @Column(name = "pain_score")
    private String painScore;

    @Column(name = "pain_description", columnDefinition = "text")
    private String painDescription;

    @Column(name = "recovery_notes", columnDefinition = "text")
    private String recoveryNotes;

    @Column(name = "additional_observations", columnDefinition = "text")
    private String additionalObservations;

    @Column(name = "equipment_count_done", nullable = false)
    private Boolean equipmentCountDone = Boolean.FALSE;

    @Column(name = "count_status")
    private String countStatus;

    @Column(name = "nausea_vomiting", nullable = false)
    private Boolean nauseaVomiting = Boolean.FALSE;

    @Column(name = "awake_and_oriented", nullable = false)
    private Boolean awakeAndOriented = Boolean.FALSE;

    @Column(name = "tolerating_oral_fluids", nullable = false)
    private Boolean toleratingOralFluids = Boolean.FALSE;

    @Column(name = "ambulating_independently", nullable = false)
    private Boolean ambulatingIndependently = Boolean.FALSE;

    @Column(name = "voided_urine", nullable = false)
    private Boolean voidedUrine = Boolean.FALSE;

    @Column(name = "no_active_bleeding", nullable = false)
    private Boolean noActiveBleeding = Boolean.FALSE;

    @Column(name = "pain_score_4", nullable = false)
    private Boolean painScore4 = Boolean.FALSE;

    @Column(name = "activity")
    private String activity;

    @Column(name = "respiration")
    private String respiration;

    @Column(name = "circulation")
    private String circulation;

    @Column(name = "consciousness")
    private String consciousness;

    @Column(name = "oxygen_saturation_level")
    private String oxygenSaturationLevel;

    @Column(name = "aldrete_score")
    private BigDecimal aldreteScore;

    @JsonProperty("patientProcedureId")
    public Long getPatientProcedureIdJson() {
        return patientProcedure == null ? null : patientProcedure.getId();
    }
}

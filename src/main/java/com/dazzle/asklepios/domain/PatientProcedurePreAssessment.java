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
@Table(name = "patient_procedure_pre_assessment")
@Getter
@Setter
@NoArgsConstructor
public class PatientProcedurePreAssessment extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_procedure_id", nullable = false)
    @JsonIgnore
    private PatientProcedure patientProcedure;

    @Column(name = "fasting_required", nullable = false)
    private Boolean fastingRequired = Boolean.FALSE;

    @Column(name = "patient_prepared", nullable = false)
    private Boolean patientPrepared = Boolean.FALSE;

    @Column(name = "special_instructions", columnDefinition = "text")
    private String specialInstructions;

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

    @Column(name = "patient_identity_verified", nullable = false)
    private Boolean patientIdentityVerified = Boolean.FALSE;

    @Column(name = "consent_confirmed", nullable = false)
    private Boolean consentConfirmed = Boolean.FALSE;

    @Column(name = "procedure_site_marked", nullable = false)
    private Boolean procedureSiteMarked = Boolean.FALSE;

    @Column(name = "allergies_confirmed", nullable = false)
    private Boolean allergiesConfirmed = Boolean.FALSE;

    @Column(name = "patient_premedicated", nullable = false)
    private Boolean patientPremedicated = Boolean.FALSE;

    @Column(name = "equipment_counting_done", nullable = false)
    private Boolean equipmentCountingDone = Boolean.FALSE;

    @Column(name = "asa_score")
    private String asaScore;

    @Column(name = "airway_grade")
    private String airwayGrade;

    @JsonProperty("patientProcedureId")
    public Long getPatientProcedureIdJson() {
        return patientProcedure == null ? null : patientProcedure.getId();
    }
}

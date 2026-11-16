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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patient_procedure_vital_monitoring")
@Getter
@Setter
@NoArgsConstructor
public class PatientProcedureVitalMonitoring extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_procedure_id", nullable = false)
    @JsonIgnore
    private PatientProcedure patientProcedure;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "heart_rate")
    private BigDecimal heartRate;

    @Column(name = "oxygen_saturation")
    private BigDecimal oxygenSaturation;

    @Column(name = "blood_pressure_systolic")
    private BigDecimal bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private BigDecimal bloodPressureDiastolic;

    @Column(name = "respiratory_rate")
    private BigDecimal respiratoryRate;

    @Column(name = "temperature")
    private BigDecimal temperature;

    @Column(name = "etco2")
    private BigDecimal etco2;

    @Column(name = "fluids_given")
    private BigDecimal fluidsGiven;

    @Column(name = "urine_output")
    private BigDecimal urineOutput;

    @JsonProperty("patientProcedureId")
    public Long getPatientProcedureIdJson() {
        return patientProcedure == null ? null : patientProcedure.getId();
    }
}

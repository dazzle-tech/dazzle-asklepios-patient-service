package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DialysisAccessSite;
import com.dazzle.asklepios.domain.enumeration.DialysisAccessType;
import com.dazzle.asklepios.domain.enumeration.DialysisAnticoagulation;
import com.dazzle.asklepios.domain.enumeration.DialysisComplication;
import com.dazzle.asklepios.domain.enumeration.DialysisDisposition;
import com.dazzle.asklepios.domain.enumeration.DialysisShift;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "dialysis_session")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialysisSession
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

    @Column(name = "chair_station", length = 500)
    private String chairStation;

    @Column(name = "machine", length = 500)
    private String machine;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", length = 50)
    private DialysisShift shift;

    @Column(name = "assigned_nurse_id")
    private Long assignedNurseId;

    @Column(name = "nephrologist_id")
    private Long nephrologistId;

    @Column(name = "pre_weight", precision = 10, scale = 2)
    private BigDecimal preWeight;

    @Column(name = "dry_weight", precision = 10, scale = 2)
    private BigDecimal dryWeight;

    @Column(name = "weight_gain", precision = 10, scale = 2)
    private BigDecimal weightGain;

    @Column(name = "interdialytic_weight_gain", precision = 10, scale = 2)
    private BigDecimal interdialyticWeightGain;

    @Column(name = "blood_pressure_systolic")
    private Integer bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private Integer bloodPressureDiastolic;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "oxygen_saturation", precision = 5, scale = 2)
    private BigDecimal oxygenSaturation;

    @Column(name = "symptoms", length = 500)
    private String symptoms;

    @Column(name = "edema", length = 500)
    private String edema;

    @Column(name = "pre_access_condition", length = 500)
    private String preAccessCondition;

    @Column(name = "general_condition", length = 500)
    private String generalCondition;

    @Column(name = "dialysis_duration", precision = 10, scale = 2)
    private BigDecimal dialysisDuration;

    @Column(name = "blood_flow_rate", precision = 10, scale = 2)
    private BigDecimal bloodFlowRate;

    @Column(name = "dialysate_flow_rate", precision = 10, scale = 2)
    private BigDecimal dialysateFlowRate;

    @Column(name = "ultrafiltration_goal", precision = 10, scale = 2)
    private BigDecimal ultrafiltrationGoal;

    @Column(name = "dialysate_composition", length = 500)
    private String dialysateComposition;

    @Column(name = "sodium", precision = 10, scale = 2)
    private BigDecimal sodium;

    @Column(name = "potassium", precision = 10, scale = 2)
    private BigDecimal potassium;

    @Column(name = "calcium", precision = 10, scale = 2)
    private BigDecimal calcium;

    @Column(name = "dialysis_temperature", precision = 10, scale = 2)
    private BigDecimal dialysisTemperature;

    @Column(name = "heparin_dose", precision = 10, scale = 2)
    private BigDecimal heparinDose;

    @Enumerated(EnumType.STRING)
    @Column(name = "anticoagulation", length = 50)
    private DialysisAnticoagulation anticoagulation;

    @Column(name = "other_anticoagulation", length = 500)
    private String otherAnticoagulation;

    @Column(name = "target_dry_weight", precision = 10, scale = 2)
    private BigDecimal targetDryWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", length = 50)
    private DialysisAccessType accessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_site", length = 50)
    private DialysisAccessSite accessSite;

    @Column(name = "other_access_site", length = 500)
    private String otherAccessSite;

    @Column(name = "infection")
    private Boolean infection;

    @Column(name = "bleeding")
    private Boolean bleeding;

    @Column(name = "thrill", length = 500)
    private String thrill;

    @Column(name = "bruit", length = 500)
    private String bruit;

    @Column(name = "dressing", length = 500)
    private String dressing;

    @Column(name = "catheter_condition", length = 500)
    private String catheterCondition;

    @Column(name = "post_weight", precision = 10, scale = 2)
    private BigDecimal postWeight;

    @Column(name = "post_blood_pressure_systolic")
    private Integer postBloodPressureSystolic;

    @Column(name = "post_blood_pressure_diastolic")
    private Integer postBloodPressureDiastolic;

    @Column(name = "post_pulse")
    private Integer postPulse;

    @Column(name = "post_temperature", precision = 5, scale = 2)
    private BigDecimal postTemperature;

    @Column(name = "total_uf_removed", precision = 10, scale = 2)
    private BigDecimal totalUfRemoved;

    @Column(name = "actual_treatment_duration", precision = 10, scale = 2)
    private BigDecimal actualTreatmentDuration;

    @Column(name = "post_access_condition", length = 500)
    private String postAccessCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", length = 50)
    private DialysisDisposition disposition;

    @Column(name = "post_complications", length = 500)
    private String postComplications;

    @Column(name = "patient_condition", length = 500)
    private String patientCondition;

    @Column(name = "target_uf", precision = 10, scale = 2)
    private BigDecimal targetUf;

    @Column(name = "actual_uf", precision = 10, scale = 2)
    private BigDecimal actualUf;

    @Column(name = "uf_difference", precision = 10, scale = 2)
    private BigDecimal ufDifference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "complications", columnDefinition = "json")
    private List<DialysisComplication> complications;

    @Column(name = "complication_notes", length = 500)
    private String complicationNotes;

    @NotNull
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Override
    public Long getId() {
        return id;
    }
}

package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DialysisAccessSite;
import com.dazzle.asklepios.domain.enumeration.DialysisAccessType;
import com.dazzle.asklepios.domain.enumeration.DialysisRegistrationStatus;
import com.dazzle.asklepios.domain.enumeration.DialysisScheduleDay;
import com.dazzle.asklepios.domain.enumeration.DialysisTreatmentType;
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
import java.util.List;

@Entity
@Table(name = "nephrology_treatment_plan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyTreatmentPlan
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
    @Column(name = "treatment_type", length = 50)
    private DialysisTreatmentType treatmentType;

    @Column(name = "frequency")
    private Integer frequency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule", columnDefinition = "json")
    private List<DialysisScheduleDay> schedule;

    @Column(name = "dialysis_duration", precision = 10, scale = 2)
    private BigDecimal dialysisDuration;

    @Column(name = "dry_weight", precision = 10, scale = 2)
    private BigDecimal dryWeight;

    @Column(name = "target_weight", precision = 10, scale = 2)
    private BigDecimal targetWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "dialysis_access", length = 50)
    private DialysisAccessType dialysisAccess;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_site", length = 50)
    private DialysisAccessSite accessSite;

    @Column(name = "other_access_site", length = 500)
    private String otherAccessSite;

    @Column(name = "blood_flow_rate", precision = 10, scale = 2)
    private BigDecimal bloodFlowRate;

    @Column(name = "dialysate_flow", precision = 10, scale = 2)
    private BigDecimal dialysateFlow;

    @Column(name = "dialysate", length = 500)
    private String dialysate;

    @Column(name = "nephrologist_id")
    private Long nephrologistId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private DialysisRegistrationStatus status;

    @NotNull
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Override
    public Long getId() {
        return id;
    }
}

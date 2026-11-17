package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.domain.enumeration.TreatmentScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dental_planned_treatment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalPlannedTreatment extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** Planning encounter stays on encounterId. Set when work actually starts or completes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_encounter_id")
    private PatientEncounter executionEncounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_plan_id")
    private DentalTreatmentPlan treatmentPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_session_id")
    private DentalTreatmentSession treatmentSession;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PlannedTreatmentType type;

    @Column(name = "visit_number")
    private Integer visitNumber;

    @NotNull
    @Column(name = "cdt_code_id", nullable = false)
    private Long cdtCodeId;

    @Column(name = "tooth_id")
    private Long toothId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "treatment_scope", nullable = false, length = 50)
    @Builder.Default
    private TreatmentScope treatmentScope = TreatmentScope.SPECIFIC_TOOTH;

    /**
     * Comma-separated covered chart-tooth IDs for arch / full-mouth rows.
     * Null for specific-tooth rows (which use {@link #toothId}).
     */
    @Column(name = "covered_tooth_ids", columnDefinition = "TEXT")
    private String coveredToothIds;

    /**
     * JSON snapshot of clinical fields (anesthesia/dose/unit/material).
     * Not stored as four separate columns.
     */
    @Column(name = "clinical_details", columnDefinition = "TEXT")
    private String clinicalDetails;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "surface")
    private ToothSurface surface;

    @Column(name = "insurance", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal insurance = BigDecimal.ZERO;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "procedure_id")
    private Long procedureId;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Long quantity = 1L;

    @Column(name = "discount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PlannedTreatmentStatus status = PlannedTreatmentStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_service_and_product_id")
    private PatientServiceAndProduct patientServiceAndProduct;

    /** Login of the user who completed the treatment. Null until completion. */
    @Column(name = "completed_by")
    private String completedBy;

    /** Server time of completion. Null until completion. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private ServiceSource source;

    @Column(name = "tooth_action_id")
    private Long toothActionId;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;
}

package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DentalTreatmentPlanStatus;
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
import java.time.Instant;

/**
 * Header for a patient's dental treatment plan. Distinct from
 * {@link DentalPlannedTreatment}, which is one planned procedure row.
 */
@Entity
@Table(name = "dental_treatment_plan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalTreatmentPlan extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "plan_number", nullable = false, unique = true, length = 40)
    private String planNumber;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_encounter_id", nullable = false)
    private PatientEncounter originEncounter;

    @NotNull
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "practitioner_id")
    private Long practitionerId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DentalTreatmentPlanStatus status = DentalTreatmentPlanStatus.DRAFT;

    /** Status to restore when an On Hold plan is resumed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "resume_status", length = 30)
    private DentalTreatmentPlanStatus resumeStatus;

    @Column(name = "hold_reason", columnDefinition = "TEXT")
    private String holdReason;

    @Column(name = "completed_by", length = 50)
    private String completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;
}

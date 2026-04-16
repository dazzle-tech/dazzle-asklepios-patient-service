package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "urgent_care_medication_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class UrgentCareMedicationOrder extends AbstractAuditingEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @NotNull
    @Column(name = "active_ingredient_id", nullable = false)
    private Long activeIngredientId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "instruction_type", nullable = false, length = 50)
    private MedicationInstructionType instructionType;

    @Column(name = "instruction_text", columnDefinition = "text")
    private String instructionText;

    @Column(name = "dose")
    private Long dose;

    @Column(name = "dose_unit", length = 100)
    private String doseUnit;

    @Column(name = "route", length = 100)
    private String route;

    @Column(name = "frequency", length = 100)
    private String frequency;

    @Column(name = "is_high_alert", nullable = false)
    private Boolean isHighAlert = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MedicationOrderStatus status;

    @Column(name = "submitted_date")
    private Instant submittedDate;

    @Column(name = "submitted_by", length = 50)
    private String submittedBy;

    @Column(name = "administered_date")
    private Instant administeredDate;

    @Column(name = "administered_by", length = 50)
    private String administeredBy;

    @Column(name = "double_checked_date")
    private Instant doubleCheckedDate;

    @Column(name = "double_checked_by", length = 50)
    private String doubleCheckedBy;

    @Column(name = "discarded_date")
    private Instant discardedDate;

    @Column(name = "discarded_by", length = 50)
    private String discardedBy;

    @Column(name = "discard_reason", length = 250)
    private String discardReason;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancellation_reason", length = 250)
    private String cancellationReason;
}

        package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.MedFrequency;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.UOM;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "claim_encounter_copy_current_medications")
@EqualsAndHashCode(callSuper = false)
public class ClaimEncounterCopyCurrentMedication
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_encounter_copy_id", nullable = false)
    private Long claimEncounterCopyId;

    @Column(name = "current_medication_id")
    private Long currentMedicationId;

    @Column(name = "active_ingredient_id")
    private Long activeIngredientId;

    @Column(name = "dosage", precision = 10, scale = 3)
    private BigDecimal dosage;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", length = 50)
    private UOM unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 100)
    private MedFrequency frequency;

    @Column(name = "start_date")
    @Temporal(TemporalType.DATE)
    private Date startDate;

    @Column(name = "patient_is_free", nullable = false)
    @Builder.Default
    private Boolean patientIsFree = Boolean.FALSE;

    @Column(name = "free_text", columnDefinition = "text")
    private String freeText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PatientHistoryStatus status = PatientHistoryStatus.ACTIVE;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;
}

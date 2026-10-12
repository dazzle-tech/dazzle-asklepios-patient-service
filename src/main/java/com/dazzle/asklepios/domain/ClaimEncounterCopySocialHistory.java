package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;

@Entity
@Table(name = "claim_encounter_copy_social_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimEncounterCopySocialHistory
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_encounter_copy_id", nullable = false)
    private Long claimEncounterCopyId;

    @Column(name = "social_history_id")
    private Long socialHistoryId;

    @Column(name = "is_current_smoker")
    private Boolean isCurrentSmoker;

    @Column(name = "smoke_start_date")
    @Temporal(TemporalType.DATE)
    private Date smokeStartDate;

    @Column(name = "cigarette_amount")
    private Integer cigaretteAmount;

    @Column(name = "cigarette_type", length = 255)
    private String cigaretteType;

    @Column(name = "is_previous_smoker")
    private Boolean isPreviousSmoker;

    @Column(name = "smoke_quit_date")
    @Temporal(TemporalType.DATE)
    private Date smokeQuitDate;

    @Column(name = "exposure_to_second_hand_smoke")
    private Boolean exposureToSecondHandSmoke;

    @Column(name = "alcohol_consumption")
    private Boolean alcoholConsumption;

    @Column(name = "type_of_alcohol", length = 255)
    private String typeOfAlcohol;

    @Column(name = "alcohol_since_when")
    @Temporal(TemporalType.DATE)
    private Date alcoholSinceWhen;

    @Column(name = "substance_use")
    private Boolean substanceUse;

    @Column(name = "route", length = 50)
    private String route;

    @Column(name = "frequency", length = 50)
    private String frequency;

    @Column(name = "physical_limitation", length = 50)
    private String physicalLimitation;

    @Column(name = "diagnosed_eating_disorders", length = 50)
    private String diagnosedEatingDisorders;

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
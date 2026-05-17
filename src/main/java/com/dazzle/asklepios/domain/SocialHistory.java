package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
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
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "social_history")
@EqualsAndHashCode(callSuper = false)
public class SocialHistory extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PatientHistoryStatus status = PatientHistoryStatus.ACTIVE;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date cancelledDate;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
}
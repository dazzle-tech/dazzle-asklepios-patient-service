package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(  name = "patient_insurances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientInsurance extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Column(name = "payor_id", nullable = false)
    private Long payorId;

    @NotNull
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "policy_holder_id")
    private Long policyHolderId;

    @NotBlank
    @Column(name = "policy_number", nullable = false, length = 100)
    private String policyNumber;

    @Column(name = "group_number", length = 100)
    private String groupNumber;

    @Future @NotNull
    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(name = "remaining_benefits", precision = 19, scale = 2)
    private BigDecimal remainingBenefits;

    @Column(name = "remaining_deductibles", precision = 19, scale = 2)
    private BigDecimal remainingDeductibles;

    @Column(name = "member_card_id", length = 100)
    private String memberCardId;

    @Column(name = "payer_nphies_id", length = 100)
    private String payerNphiesId;

    @Column(name = "network_id", length = 100)
    private String networkId;

    @Column(name = "sponsor_number", length = 100)
    private String sponsorNumber;

    @Column(name = "coverage_type", length = 50)
    private String coverageType;

    @Column(name = "relation_with_subscriber", length = 50)
    private String relationWithSubscriber;

    @Column(name = "policy_class_name", length = 100)
    private String policyClassName;

    @Column(name = "policy_holder_name", length = 255)
    private String policyHolderName;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "patient_share", precision = 19, scale = 2)
    private BigDecimal patientShare;

    @Column(name = "max_limit", precision = 19, scale = 2)
    private BigDecimal maxLimit;

    @Column(name = "waseel_new_plan")
    private Boolean waseelNewPlan;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;
}

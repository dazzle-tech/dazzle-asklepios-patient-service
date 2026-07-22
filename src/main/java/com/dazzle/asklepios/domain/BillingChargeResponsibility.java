package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PreAuthorizationStatus;
import com.dazzle.asklepios.domain.enumeration.ResponsibilityRole;
import com.dazzle.asklepios.domain.enumeration.ResponsiblePartyType;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_charge_responsibility")
public class BillingChargeResponsibility extends AbstractAuditingEntity<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    private BillingCharge charge;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_line_id", nullable = false)
    private BillingChargeLine chargeLine;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_service_product_id", nullable = false)
    private PatientServicesAndProducts patientServiceProduct;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "responsible_party_type", nullable = false, length = 30)
    private ResponsiblePartyType responsiblePartyType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "responsibility_role", nullable = false, length = 30)
    private ResponsibilityRole responsibilityRole;

    @Column(name = "payer_id")
    private Long payerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_insurance_id")
    private PatientInsurance patientInsurance;

    @Size(max = 100)
    @Column(name = "policy_number", length = 100)
    private String policyNumber;

    @Size(max = 100)
    @Column(name = "member_number", length = 100)
    private String memberNumber;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "responsibility_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal responsibilityAmount;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAmount;

    @NotNull
    @DecimalMin(value = "0.000000")
    @DecimalMax(value = "100.000000")
    @Column(name = "coverage_percentage", nullable = false, precision = 19, scale = 6)
    private BigDecimal coveragePercentage = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "deductible_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal deductibleAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "copay_amount", nullable = false, precision = 19,scale = 4)
    private BigDecimal copayAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "coinsurance_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal coinsuranceAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "non_covered_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal nonCoveredAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "contractual_adjustment_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal contractualAdjustmentAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingResponsibilityStatus status = BillingResponsibilityStatus.CALCULATED;

    @NotNull
    @Column(name = "pre_authorization_required", nullable = false)
    private Boolean preAuthorizationRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_authorization_status", length = 50)
    private PreAuthorizationStatus preAuthorizationStatus;

    @Size(max = 100)
    @Column(name = "pre_authorization_reference_no", length = 100)
    private String preAuthorizationReferenceNo;

    @Column(name = "claim_id")
    private Long claimId;

    @Size(max = 150)
    @Column(name = "claim_reference", length = 150)
    private String claimReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_responsibility_id")
    private BillingChargeResponsibility supersededResponsibility;

    @Size(max = 500)
    @Column(name = "adjustment_reason", length = 500)
    private String adjustmentReason;

    @NotNull
    @Column(name = "effective_date", nullable = false)
    private Instant effectiveDate;

    @Column(name = "closed_date")
    private Instant closedDate;

    @NotNull
    @Size(max = 150)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;
}
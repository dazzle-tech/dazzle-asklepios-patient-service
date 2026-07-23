package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.client.setup.dto.BrandMedicationSetupDTO;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingItemType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
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
@Table(name = "billing_charge_line")
public class BillingChargeLine extends AbstractAuditingEntity<Long>
        implements Serializable {

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
    @JoinColumn(name = "patient_service_product_id", nullable = false)
    private PatientServiceAndProduct patientServiceProduct;

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
    @Column(name = "billing_item_type", nullable = false, length = 50)
    private BillingItemTypes billingItemType;

    @Column(name = "brand_medication_id")
    private Long brandMedicationId;

    @Column(name = "diagnostic_test_id")
    private Long diagnosticTestId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "procedure_id")
    private Long procedureId;

    @Size(max = 100)
    @Column(name = "item_code", length = 100)
    private String itemCode;

    @NotNull
    @Size(max = 500)
    @Column(name = "item_description", nullable = false, length = 500)
    private String itemDescription;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "service_source", nullable = false, length = 50)
    private ServiceSource serviceSource;

    @Column(name = "source_id")
    private Long sourceId;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "exemption_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal exemptionAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "patient_responsibility_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal patientResponsibilityAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "insurance_responsibility_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal insuranceResponsibilityAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "other_payer_responsibility_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal otherPayerResponsibilityAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingChargeLineStatus status = BillingChargeLineStatus.DRAFT;

    @Column(name = "current_pricing_snapshot_id")
    private Long currentPricingSnapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_insurance_id")
    private PatientInsurance patientInsurance;

    @NotNull
    @Column(name = "pre_authorization_required", nullable = false)
    private Boolean preAuthorizationRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_authorization_status", length = 50)
    private PreAuthorizationStatus preAuthorizationStatus;

    @Column(name = "pre_authorization_request_id")
    private Long preAuthorizationRequestId;

    @Size(max = 100)
    @Column(name = "pre_authorization_reference_no", length = 100)
    private String preAuthorizationReferenceNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_charge_line_id")
    private BillingChargeLine reversedChargeLine;

    @NotNull
    @Size(max = 150)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Size(max = 50)
    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Size(max = 500)
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

}
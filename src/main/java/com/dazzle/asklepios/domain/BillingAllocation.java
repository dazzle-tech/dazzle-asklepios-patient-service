package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AllocationSourceType;
import com.dazzle.asklepios.domain.enumeration.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
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
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_allocation")
public class BillingAllocation extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "allocation_number", nullable = false, unique = true, length = 50)
    private String allocationNumber;

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
    @JoinColumn(name = "charge_responsibility_id", nullable = false)
    private BillingChargeResponsibility chargeResponsibility;

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
    @Column(name = "allocation_source_type", nullable = false, length = 40)
    private AllocationSourceType allocationSourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private BillingReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private BillingPayment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private BillingPaymentTransaction paymentTransaction;


    @Column(name = "debit_transaction_id")
    private Long debitTransactionId;

    @Size(max = 50)
    @Column(name = "source_reference_type", length = 50)
    private String sourceReferenceType;

    @Column(name = "source_reference_id")
    private Long sourceReferenceId;

    @Size(max = 150)
    @Column(name = "source_reference_number", length = 150)
    private String sourceReferenceNumber;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "remaining_allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingAllocatedAmount;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "reversed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal reversedAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingAllocationStatus status = BillingAllocationStatus.ACTIVE;

    @NotNull
    @Column(name = "allocation_date", nullable = false)
    private Instant allocationDate;

    @Column(name = "reversed_date")
    private Instant reversedDate;

    @Size(max = 50)
    @Column(name = "reversed_by", length = 50)
    private String reversedBy;

    @Size(max = 500)
    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_allocation_id")
    private BillingAllocation originalAllocation;

    @NotNull
    @Size(max = 150)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @NotNull
    @Column(name = "transaction_group_id", nullable = false)
    private UUID transactionGroupId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
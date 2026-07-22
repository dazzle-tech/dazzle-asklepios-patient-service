package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BillingRefundSourceType;
import com.dazzle.asklepios.domain.enumeration.BillingRefundStatus;
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
@Table(name = "billing_refund")
public class BillingRefund extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "refund_number", nullable = false, unique = true, length = 50)
    private String refundNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private BillingWallet wallet;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private PatientEncounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_id")
    private BillingPayment originalPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_transaction_id")
    private BillingPaymentTransaction originalPaymentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_payment_transaction_id")
    private BillingPaymentTransaction refundPaymentTransaction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_source_type", nullable = false, length = 40)
    private BillingRefundSourceType refundSourceType;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "approved_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal approvedAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "reversed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal reversedAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @Size(max = 50)
    @Column(name = "refund_method_code", nullable = false, length = 50)
    private String refundMethodCode;

    @Column(name = "refund_method_id")
    private Long refundMethodId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingRefundStatus status = BillingRefundStatus.REQUESTED;

    @NotNull
    @Column(name = "request_date", nullable = false)
    private Instant requestDate;

    @NotNull
    @Size(max = 50)
    @Column(name = "requested_by", nullable = false, length = 50)
    private String requestedBy;

    @NotNull
    @Size(max = 500)
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Size(max = 50)
    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_date")
    private Instant approvedDate;

    @Size(max = 50)
    @Column(name = "rejected_by", length = 50)
    private String rejectedBy;

    @Column(name = "rejected_date")
    private Instant rejectedDate;

    @Size(max = 500)
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Size(max = 50)
    @Column(name = "completed_by", length = 50)
    private String completedBy;

    @Column(name = "completed_date")
    private Instant completedDate;

    @Size(max = 50)
    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Size(max = 500)
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Size(max = 50)
    @Column(name = "reversed_by", length = 50)
    private String reversedBy;

    @Column(name = "reversed_date")
    private Instant reversedDate;

    @Size(max = 500)
    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Size(max = 150)
    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Size(max = 150)
    @Column(name = "processor_reference", length = 150)
    private String processorReference;

    @Size(max = 50)
    @Column(name = "reference_document_type", length = 50)
    private String referenceDocumentType;

    @Column(name = "reference_document_id")
    private Long referenceDocumentId;

    @Size(max = 150)
    @Column(name = "reference_document_number", length = 150)
    private String referenceDocumentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_refund_id")
    private BillingRefund originalRefund;

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
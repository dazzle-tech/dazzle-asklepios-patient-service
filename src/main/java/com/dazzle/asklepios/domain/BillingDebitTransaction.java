package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BillingDebitTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.BillingDebitTransactionType;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_debit_transaction")
public class BillingDebitTransaction extends AbstractAuditingEntity<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "transaction_number", nullable = false, unique = true, length = 50)
    private String transactionNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debit_account_id", nullable = false)
    private BillingDebitAccount debitAccount;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private PatientEncounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_responsibility_id")
    private BillingChargeResponsibility chargeResponsibility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private BillingCharge charge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_line_id")
    private BillingChargeLine chargeLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_service_product_id")
    private PatientServiceAndProduct patientServiceProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private BillingPayment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private BillingPaymentTransaction paymentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_transaction_id")
    private BillingDebitTransaction parentTransaction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private BillingDebitTransactionType transactionType;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "balance_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingDebitTransactionStatus status = BillingDebitTransactionStatus.COMPLETED;

    @NotNull
    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "settled_date")
    private Instant settledDate;

    @Size(max = 50)
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Size(max = 150)
    @Column(name = "reference_number", length = 150)
    private String referenceNumber;

    @Size(max = 500)
    @Column(name = "reason", length = 500)
    private String reason;

    @Size(max = 50)
    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_date")
    private Instant approvedDate;

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
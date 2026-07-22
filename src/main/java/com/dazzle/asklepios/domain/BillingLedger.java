package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BillingLedgerEntryCategory;
import com.dazzle.asklepios.domain.enumeration.BillingLedgerEntryDirection;
import com.dazzle.asklepios.domain.enumeration.BillingLedgerScope;
import com.dazzle.asklepios.domain.enumeration.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.BillingLedgerTransactionType;
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
@Table(name = "billing_ledger")
public class BillingLedger extends AbstractAuditingEntity<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "ledger_number", nullable = false, unique = true, length = 50)
    private String ledgerNumber;

    @NotNull
    @Column(name = "transaction_group_id", nullable = false)
    private UUID transactionGroupId;

    @Size(max = 150)
    @Column(name = "correlation_id", length = 150)
    private String correlationId;

    @NotNull
    @Size(max = 150)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private PatientEncounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private BillingWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private BillingPayment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private BillingPaymentTransaction paymentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private BillingCharge charge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_line_id")
    private BillingChargeLine chargeLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_responsibility_id")
    private BillingChargeResponsibility chargeResponsibility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private BillingReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id")
    private BillingAllocation allocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debit_account_id")
    private BillingDebitAccount debitAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debit_transaction_id")
    private BillingDebitTransaction debitTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private BillingRefund refund;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 60)
    private BillingLedgerTransactionType transactionType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_scope", nullable = false, length = 30)
    private BillingLedgerScope ledgerScope;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @Column(name = "available_change", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableChange = BigDecimal.ZERO;

    @NotNull
    @Column(name = "reserved_change", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedChange = BigDecimal.ZERO;

    @NotNull
    @Column(name = "consumed_change", nullable = false, precision = 19, scale = 4)
    private BigDecimal consumedChange = BigDecimal.ZERO;

    @NotNull
    @Column(name = "refunded_change", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedChange = BigDecimal.ZERO;

    @NotNull
    @Column(name = "debit_balance_change", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitBalanceChange = BigDecimal.ZERO;

    @NotNull
    @Column(name = "allocated_change", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedChange = BigDecimal.ZERO;

    @NotNull
    @Column(name = "outstanding_change", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingChange = BigDecimal.ZERO;

    @Column(name = "wallet_available_before", precision = 19, scale = 4)
    private BigDecimal walletAvailableBefore;

    @Column(name = "wallet_available_after", precision = 19, scale = 4)
    private BigDecimal walletAvailableAfter;

    @Column(name = "wallet_reserved_before", precision = 19, scale = 4)
    private BigDecimal walletReservedBefore;

    @Column(name = "wallet_reserved_after", precision = 19, scale = 4)
    private BigDecimal walletReservedAfter;

    @Column(name = "debit_balance_before", precision = 19, scale = 4)
    private BigDecimal debitBalanceBefore;

    @Column(name = "debit_balance_after", precision = 19, scale = 4)
    private BigDecimal debitBalanceAfter;

    @Column(name = "responsibility_outstanding_before", precision = 19, scale = 4)
    private BigDecimal responsibilityOutstandingBefore;

    @Column(name = "responsibility_outstanding_after", precision = 19, scale = 4)
    private BigDecimal responsibilityOutstandingAfter;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_direction", nullable = false, length = 10)
    private BillingLedgerEntryDirection entryDirection;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_category", nullable = false, length = 30)
    private BillingLedgerEntryCategory entryCategory = BillingLedgerEntryCategory.BUSINESS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_ledger_id")
    private BillingLedger reversedLedger;

    @Size(max = 50)
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Size(max = 150)
    @Column(name = "reference_number", length = 150)
    private String referenceNumber;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @Size(max = 500)
    @Column(name = "reason", length = 500)
    private String reason;

    @NotNull
    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 30)
    private BillingLedgerSourceChannel sourceChannel;
}
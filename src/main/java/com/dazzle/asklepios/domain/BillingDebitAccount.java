package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitAccountStatus;
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

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_debit_account")
public class BillingDebitAccount extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "account_number", nullable = false, unique = true, length = 50)
    private String accountNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "current_debit_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentDebitBalance = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "available_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableCredit = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "total_debit_created", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebitCreated = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "total_debit_settled", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebitSettled = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingDebitAccountStatus status = BillingDebitAccountStatus.ACTIVE;

    @NotNull
    @Column(name = "debit_allowed", nullable = false)
    private Boolean debitAllowed = false;

    @NotNull
    @Column(name = "approval_required", nullable = false)
    private Boolean approvalRequired = false;

    @Size(max = 50)
    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_date")
    private Instant approvedDate;

    @Size(max = 50)
    @Column(name = "blocked_by", length = 50)
    private String blockedBy;

    @Column(name = "blocked_date")
    private Instant blockedDate;

    @Size(max = 500)
    @Column(name = "block_reason", length = 500)
    private String blockReason;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
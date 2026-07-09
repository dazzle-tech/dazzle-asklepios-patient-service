package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentLifecycleStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentMethods;
import com.dazzle.asklepios.domain.enumeration.PaymentTypes;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "patient_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientPayments extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private PatientInsurance plan;

    @NotNull
    @Column(name = "due_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal dueAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "patient_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal patientBalance = BigDecimal.ZERO;

    @NotNull
    @Column(name = "paid_from_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidFromAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "paid_from_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidFromBalance = BigDecimal.ZERO;

    @NotNull
    @Column(name = "refunds", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal refunds = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 50)
    private PaymentTypes paymentTypes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethods paymentMethods;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "facility_default_currency", nullable = false, length = 10)
    private Currency facilityDefaultCurrency;

    @Column(name = "amount_in_facility_currency", precision = 19, scale = 4)
    private BigDecimal amountInFacilityCurrency;

    @NotNull
    @Column(name = "remaining", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal remaining = BigDecimal.ZERO;

    @NotNull
    @Column(name = "add_to_free_balance", nullable = false)
    private Boolean addToFreeBalance;

    // Toggle: allow wallet to settle older open charges
    @NotNull
    @Column(name = "use_balance_to_settle_debts", nullable = false)
    @Builder.Default
    private Boolean useBalanceToSettleDebts = Boolean.FALSE;

    @Column(name = "card_number", length = 25)
    private String cardNumber;

    @Column(name = "card_holder_name", length = 255)
    private String cardHolderName;

    @Column(name = "card_valid_until")
    private LocalDate cardValidUntil;

    @Column(name = "cheque_number", length = 50)
    private String chequeNumber;

    @Column(name = "cheque_bank_name", length = 255)
    private String chequeBankName;

    @Column(name = "cheque_due_date")
    private LocalDate chequeDueDate;

    @Column(name = "transfer_number", length = 50)
    private String transferNumber;

    @Column(name = "transfer_bank_name", length = 255)
    private String transferBankName;

    @Column(name = "transfer_date")
    private LocalDate transferDate;

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<PatientPaymentServices> services = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PaymentLifecycleStatus status = PaymentLifecycleStatus.CREATED;

    @AssertTrue(message = "plan is required when paymentType is INSURANCE_PLAN")
    private boolean isPlanValid() {
        if (PaymentTypes.INSURANCE_PLAN.equals(this.paymentTypes)) {
            return this.plan != null;
        }
        return this.plan == null;
    }
}
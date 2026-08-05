package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.billing.BillingReservationStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.ReservationReleaseReason;
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
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_reservation")
public class BillingReservation extends AbstractAuditingEntity<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "reservation_number", nullable = false, unique = true, length = 50)
    private String reservationNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private BillingWallet wallet;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private BillingPayment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private BillingPaymentTransaction paymentTransaction;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

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
    private PatientServiceAndProduct patientServiceProduct;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "original_reserved_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalReservedAmount;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "remaining_reserved_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingReservedAmount;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "consumed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal consumedAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Column(name = "released_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal releasedAmount = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingReservationStatus status = BillingReservationStatus.ACTIVE;

    @NotNull
    @Column(name = "reserved_date", nullable = false)
    private Instant reservedDate;

    @Column(name = "consumed_date")
    private Instant consumedDate;

    @Column(name = "released_date")
    private Instant releasedDate;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 50)
    private ReservationReleaseReason releaseReason;

    @Size(max = 500)
    @Column(name = "release_notes", length = 500)
    private String releaseNotes;

    @Size(max = 50)
    @Column(name = "released_by", length = 50)
    private String releasedBy;

    @Size(max = 50)
    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Size(max = 500)
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_reservation_id")
    private BillingReservation previousReservation;

    @NotNull
    @Size(max = 150)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @NotNull
    @Column(name = "transaction_group_id", nullable = false)
    private UUID transactionGroupId;
}
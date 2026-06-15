package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "patient_payment_allocations",
        uniqueConstraints = @UniqueConstraint(name = "uq_alloc_payment_charge", columnNames = {"payment_id", "charge_id"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientPaymentAllocation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "charge_id", nullable = false)
    private Long chargeId;

    @Column(name = "paid_from_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidFromAmount = BigDecimal.ZERO;

    @Column(name = "paid_from_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidFromBalance = BigDecimal.ZERO;

    @Column(name = "last_modified_date", nullable = false)
    private Instant lastModifiedDate;

}
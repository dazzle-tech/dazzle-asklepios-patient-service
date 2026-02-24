package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "patient_payment_services")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientPaymentServices extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PatientPayments payment;

    @NotNull
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @NotNull
    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @NotNull
    @Column(name = "is_exempted", nullable = false)
    private Boolean isExempted;
}

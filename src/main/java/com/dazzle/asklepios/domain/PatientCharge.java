package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "patient_charges",
        uniqueConstraints = @UniqueConstraint(name = "uq_patient_charges_encounter", columnNames = {"encounter_id"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientCharge implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "due_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal dueAmount = BigDecimal.ZERO;

    @Column(name = "remaining", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal remaining = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private Currency currency;

    @Column(name = "facility_default_currency", nullable = false, length = 10)
    private String facilityDefaultCurrency;

    @Column(name = "created_date", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdDate = Instant.now();

    @Column(name = "last_modified_date", nullable = false)
    @Builder.Default
    private Instant lastModifiedDate = Instant.now();
}
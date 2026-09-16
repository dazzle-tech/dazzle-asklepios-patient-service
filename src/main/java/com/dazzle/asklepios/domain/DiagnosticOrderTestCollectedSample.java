package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_order_test_collected_samples")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class DiagnosticOrderTestCollectedSample extends AbstractAuditingEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_test_id", nullable = false)
    private Long orderTestId;

    @Column(name = "unit", columnDefinition = "text")
    private String unit;

    @Column(name = "quantity", precision = 19, scale = 2)
    private BigDecimal quantity;

    @PastOrPresent(message = "collected sample at cannot be in the future")
    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @NotNull
    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Column(name = "source_of_sample", length = 100, nullable = false)
    private String sourceOfSample;

    @Column(name = "rejected", nullable = false)
    private Boolean rejected = false;

}
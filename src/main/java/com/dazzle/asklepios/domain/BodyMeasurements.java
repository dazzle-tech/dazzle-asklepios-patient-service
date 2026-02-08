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

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "body_measurements")
public class BodyMeasurements extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Column(name = "weight", precision = 6, scale = 2)
    private BigDecimal weight;

    @NotNull
    @Column(name = "height", precision = 6, scale = 2)
    private BigDecimal height;

    @Column(name = "head_circumference", precision = 6, scale = 2)
    private BigDecimal headCircumference;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

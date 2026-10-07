package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "nephrology_renal_function")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NephrologyRenalFunction
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "egfr", precision = 10, scale = 2)
    private BigDecimal egfr;

    @Column(name = "creatinine", precision = 10, scale = 2)
    private BigDecimal creatinine;

    @Column(name = "bun", precision = 10, scale = 2)
    private BigDecimal bun;

    @Column(name = "hemoglobin", precision = 10, scale = 2)
    private BigDecimal hemoglobin;

    @Column(name = "potassium", precision = 10, scale = 2)
    private BigDecimal potassium;

    @Column(name = "sodium", precision = 10, scale = 2)
    private BigDecimal sodium;

    @Column(name = "calcium", precision = 10, scale = 2)
    private BigDecimal calcium;

    @Column(name = "phosphorus", precision = 10, scale = 2)
    private BigDecimal phosphorus;

    @NotNull
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Override
    public Long getId() {
        return id;
    }
}

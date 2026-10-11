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
import java.time.LocalTime;

@Entity
@Table(name = "dialysis_flow_reading")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialysisFlowReading
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "dialysis_session_id", nullable = false)
    private Long dialysisSessionId;

    @Column(name = "time")
    private LocalTime time;

    @Column(name = "blood_pressure_systolic")
    private Integer bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private Integer bloodPressureDiastolic;

    @Column(name = "pulse")
    private Integer pulse;

    @Column(name = "uf_rate", precision = 10, scale = 2)
    private BigDecimal ufRate;

    @Column(name = "uf_removed", precision = 10, scale = 2)
    private BigDecimal ufRemoved;

    @Column(name = "arterial_pressure", precision = 10, scale = 2)
    private BigDecimal arterialPressure;

    @Column(name = "venous_pressure", precision = 10, scale = 2)
    private BigDecimal venousPressure;

    @Column(name = "transmembrane_pressure", precision = 10, scale = 2)
    private BigDecimal transmembranePressure;

    @Override
    public Long getId() {
        return id;
    }
}

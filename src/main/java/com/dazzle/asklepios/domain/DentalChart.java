package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "dental_chart")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalChart extends AbstractAuditingEntity<Long> implements Serializable {

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

    @NotNull
    @Column(name = "chart_date", nullable = false)
    private Instant chartDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "chart_type", nullable = false)
    private DentalChartType chartType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DentalChartStatus status;

    @Column(name = "treatment_plan", columnDefinition = "TEXT")
    private String treatmentPlan;
}

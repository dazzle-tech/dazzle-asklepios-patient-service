package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
import com.dazzle.asklepios.domain.enumeration.ToothNumberingSystem;
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
import jakarta.persistence.Version;
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

    /**
     * Snapshot of the Setup numbering system applied when this chart was created.
     * Later Setup edits must not rewrite existing charts.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "numbering_system", nullable = false)
    @Builder.Default
    private ToothNumberingSystem numberingSystem = ToothNumberingSystem.UNIVERSAL;

    /**
     * Snapshot of the custom Tooth Numbering method (id) and its resolved labels (JSON, tooth id -> label)
     * taken when this chart was created. Both are null for legacy charts, which keep using
     * {@link #numberingSystem}. Later method edits or deactivation never change them.
     */
    @Column(name = "numbering_method_id")
    private Long numberingMethodId;

    @Column(name = "numbering_labels", columnDefinition = "TEXT")
    private String numberingLabels;

    /**
     * Snapshot of the DentalChartAgeRule id that seeded this chart (nullable for legacy charts).
     */
    @Column(name = "age_rule_id")
    private Long ageRuleId;

    @Column(name = "treatment_plan", columnDefinition = "TEXT")
    private String treatmentPlan;

    /**
     * Optimistic lock. Every chart save, including an inventory edit, increments it, so a stale
     * inventory save is rejected whichever path changed the chart in between.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

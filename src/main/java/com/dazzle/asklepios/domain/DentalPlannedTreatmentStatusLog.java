package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentHistoryAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.Instant;

@Entity
@Table(name = "dental_planned_treatment_status_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalPlannedTreatmentStatusLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "planned_treatment_id", nullable = false)
    private Long plannedTreatmentId;

    @NotNull
    @Column(name = "treatment_plan_id", nullable = false)
    private Long treatmentPlanId;

    @Column(name = "tooth_id")
    private Long toothId;

    @Column(name = "tooth_label", length = 50)
    private String toothLabel;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private PlannedTreatmentHistoryAction action;

    @NotNull
    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @NotNull
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}

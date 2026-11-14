package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
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

/**
 * A numbered session belonging to one treatment plan.
 * Session numbers restart at 1 for each plan.
 */
@Entity
@Table(name = "dental_treatment_session")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalTreatmentSession extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_plan_id", nullable = false)
    private DentalTreatmentPlan treatmentPlan;

    @NotNull
    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;
}

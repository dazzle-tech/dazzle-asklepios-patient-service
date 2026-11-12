package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "dental_treatment_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dental_treatment_session_plan_number",
                columnNames = {"treatment_plan_id", "session_number"}
        )
)
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
    @Column(name = "treatment_plan_id", nullable = false)
    private Long treatmentPlanId;

    @NotNull
    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;
}

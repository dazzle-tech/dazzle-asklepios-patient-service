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

@Entity
@Table(name = "dialysis_medication")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialysisMedication
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "dialysis_session_id", nullable = false)
    private Long dialysisSessionId;

    @NotNull
    @Column(name = "active_ingredient_id", nullable = false)
    private Long activeIngredientId;

    @Column(name = "dose")
    private Long dose;

    @Column(name = "dose_unit", length = 100)
    private String doseUnit;

    @Override
    public Long getId() {
        return id;
    }
}

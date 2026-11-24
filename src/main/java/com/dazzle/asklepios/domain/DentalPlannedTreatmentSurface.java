package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ToothSurface;
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

/** One selected surface of a planned treatment. */
@Entity
@Table(name = "dental_planned_treatment_surface")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalPlannedTreatmentSurface implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planned_treatment_id", nullable = false)
    private DentalPlannedTreatment plannedTreatment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "surface", nullable = false, length = 50)
    private ToothSurface surface;
}

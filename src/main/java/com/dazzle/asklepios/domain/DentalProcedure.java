package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ToothNumber;
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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dental_procedure")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalProcedure extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @Enumerated(EnumType.STRING)
    @Column(name = "tooth_number", nullable = false)
    private ToothNumber toothNumber;

    @Column(name = "surface", nullable = false)
    private String surface;

    @Column(name = "anesthesia_used")
    private String anesthesiaUsed;

    @Column(name = "dose", precision = 10, scale = 2)
    private BigDecimal dose;

    @Column(name = "unit")
    private String unit;

    @Column(name = "filling_material")
    private String fillingMaterial;

    @NotNull
    @Column(name = "procedure_id", nullable = false)
    private Long procedureId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "cdt_code_id")
    private Long cdtCodeId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;
}


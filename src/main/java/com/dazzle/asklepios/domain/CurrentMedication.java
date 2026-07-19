package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.MedFrequency;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.UOM;
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
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "current_medication")
@EqualsAndHashCode(callSuper = false)
public class CurrentMedication extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "active_ingredient_id", nullable = false)
    private Long activeIngredientId;

    @Column(name = "dosage", precision = 10, scale = 3)
    private BigDecimal dosage;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", length = 50)
    private UOM unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 100)
    private MedFrequency frequency;

    @NotNull
    @Column(name = "start_date", nullable = false)
    @Temporal(TemporalType.DATE)
    private Date startDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PatientHistoryStatus status = PatientHistoryStatus.ACTIVE;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;
}
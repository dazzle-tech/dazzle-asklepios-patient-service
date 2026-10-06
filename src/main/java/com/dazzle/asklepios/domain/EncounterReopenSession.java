package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DischargeType;
import com.dazzle.asklepios.domain.enumeration.EncounterReopenSessionStatus;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.TypeOfReopen;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "encounter_reopen_session",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_encounter_reopen_session_encounter_number",
                columnNames = {"encounter_id", "session_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterReopenSession extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private PatientEncounter encounter;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_of_reopen", length = 50)
    private TypeOfReopen typeOfReopen;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_treatment_status", nullable = false, length = 50)
    private TreatmentStatus originalTreatmentStatus;

    @Column(name = "reopened_by", nullable = false, length = 50)
    private String reopenedBy;

    @Column(name = "reopened_at", nullable = false)
    private Instant reopenedAt;

    @Column(name = "closed_by", length = 50)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private EncounterReopenSessionStatus status;

    @Column(name = "completed_by", length = 50)
    private String completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "discharge_at")
    private LocalDateTime dischargeAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "discharge_type", length = 50)
    private DischargeType dischargeType;
}

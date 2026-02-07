package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Priority;
import com.dazzle.asklepios.domain.enumeration.ProcedureLevel;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "patient_procedure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientProcedure extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "procedure_id", nullable = false)
    private Long procedureId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Column(name = "from_facility_id", nullable = false)
    private Long fromFacilityId;

    @NotNull
    @Column(name = "to_facility_id", nullable = false)
    private Long toFacilityId;

    @NotNull
    @Column(name = "from_department_id", nullable = false)
    private Long fromDepartmentId;

    @Column(name = "to_department_id")
    private Long toDepartmentId;

    @Column(name = "indication_id", nullable = true)
    private Long indicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedure_level", nullable = false)
    private ProcedureLevel procedureLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private Priority priority;

    @NotBlank
    @Column(name = "body_part", nullable = false)
    private String bodyPart;

    @Column(name = "side")
    private String side;

    @FutureOrPresent
    @NotNull
    @Column(name = "scheduled_date_time")
    private Instant scheduledDateTime;

    @Column(name = "notes")
    private String notes;

    @Column(name = "extra_documentation")
    private String extraDocumentation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcStatus status;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;
}

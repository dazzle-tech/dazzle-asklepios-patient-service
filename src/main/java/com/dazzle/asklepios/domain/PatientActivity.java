package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.domain.enumeration.PatientActivitySource;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patient_activity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientActivity extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    /** The facility the task was raised in; Team Tasks and All Tasks show one facility at a time. */
    @Column(name = "facility_id")
    private Long facilityId;

    /** Code of the activity_category row. */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** Code of the activity_template row. */
    @Column(name = "activity_type", nullable = false, length = 80)
    private String activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PatientActivityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private PatientActivitySource source;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private PatientActivityPriority priority;

    @Column(name = "note", nullable = false, length = 2000)
    private String note;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by", length = 50)
    private String completedBy;

    @Column(name = "outcome", length = 100)
    private String outcome;

    @Column(name = "outcome_note", length = 2000)
    private String outcomeNote;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assigned_roles", columnDefinition = "jsonb", nullable = false)
    private List<String> assignedRoles = new ArrayList<>();

    @Column(name = "assigned_to", length = 50)
    private String assignedTo;

    /** Code of the activity plan this task came from, if any. */
    @Column(name = "plan_code", length = 80)
    private String planCode;

    /** Shared by the tasks created by one application of a plan. */
    @Column(name = "plan_run_id", length = 36)
    private String planRunId;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist", columnDefinition = "jsonb", nullable = false)
    private List<PatientActivityChecklistItem> checklist = new ArrayList<>();

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;
}

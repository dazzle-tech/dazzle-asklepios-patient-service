package com.dazzle.asklepios.domain;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_order_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class DiagnosticOrderTest extends AbstractAuditingEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private DiagnosticOrderTestStatus status;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "test_id")
    private Long testId;

    @Column(name = "received_department_id")
    private Long receivedDepartmentId;

    // Liquibase: varchar(50)
    @Column(name = "reason", length = 50)
    private String reason;

    // Liquibase: text
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    // Liquibase: varchar(50)
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 50)
    private DiagnosticStatus processingStatus;

    @Column(name = "submit_date")
    private Instant submitDate;

    @Column(name = "accepted_date")
    private Instant acceptedDate;

    @Column(name = "rejected_date")
    private Instant rejectedDate;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "patient_arrived_date")
    private Instant patientArrivedDate;

    @Column(name = "ready_date")
    private Instant readyDate;

    @Column(name = "approved_date")
    private Instant approvedDate;

    // Liquibase: varchar(50)
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 50)
    private TestType orderType;

    // Liquibase: varchar(50)
    @Column(name = "accepted_by", length = 50)
    private String acceptedBy;

    // Liquibase: varchar(50)
    @Column(name = "rejected_by", length = 50)
    private String rejectedBy;

    // Liquibase: varchar(50)
    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    // Liquibase: varchar(200)
    @Column(name = "rejected_reason", length = 200)
    private String rejectedReason;

    // Liquibase: varchar(150)
    @Column(name = "patient_arrived_note_rad", length = 150)
    private String patientArrivedNoteRad;

    // Liquibase: varchar(250)
    @Column(name = "cancellation_reason", length = 250)
    private String cancellationReason;


}

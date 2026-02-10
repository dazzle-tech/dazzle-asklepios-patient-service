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
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private DiagnosticOrderTestStatus status;

    @NotNull
    @Column(name = "order_id")
    private Long orderId;

    @NotNull
    @Column(name = "test_id")
    private Long testId;

    @Column(name = "received_department_id")
    private Long receivedDepartmentId;

    @Column(name = "reason", length = 50)
    private String reason;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @NotNull
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


    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 50)
    private TestType orderType;

    @Column(name = "accepted_by", length = 50)
    private String acceptedBy;

    @Column(name = "rejected_by", length = 50)
    private String rejectedBy;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "rejected_reason", length = 200)
    private String rejectedReason;

    @Column(name = "patient_arrived_note_rad", length = 150)
    private String patientArrivedNoteRad;

    @Column(name = "cancellation_reason", length = 250)
    private String cancellationReason;


}

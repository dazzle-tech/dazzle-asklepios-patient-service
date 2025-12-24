
package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "status", columnDefinition = "text")
    private String status;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "test_id")
    private Long testId;

    @Column(name = "received_department_id")
    private Long receivedDepartmentId;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "processing_status", columnDefinition = "text")
    private String processingStatus;

    @Column(name = "submit_date")
    private Instant submitDate;

    @Column(name = "accepted_date")
    private Instant acceptedDate;

    @Column(name = "rejected_date")
    private Instant rejectedDate;

    @Column(name = "patient_arrived_date")
    private Instant patientArrivedDate;

    @Column(name = "ready_date")
    private Instant readyDate;

    @Column(name = "approved_date")
    private Instant approvedDate;

    @Column(name = "order_type", columnDefinition = "text")
    private String orderType;

    @Column(name = "accepted_by", columnDefinition = "text")
    private String acceptedBy;

    @Column(name = "rejected_by", columnDefinition = "text")
    private String rejectedBy;

    @Column(name = "rejected_reason", columnDefinition = "text")
    private String rejectedReason;

    @Column(name = "patient_arrived_note_rad", columnDefinition = "text")
    private String patientArrivedNoteRad;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "from_department_id")
    private Long fromDepartmentId;

    @Column(name = "from_facility_id")
    private Long fromFacilityId;

    @Column(name = "to_facility_id")
    private Long toFacilityId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

}

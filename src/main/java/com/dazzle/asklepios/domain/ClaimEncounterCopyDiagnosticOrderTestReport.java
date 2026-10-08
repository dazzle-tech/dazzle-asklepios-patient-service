package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "claim_encounter_copy_diagnostic_order_test_reports")
@EqualsAndHashCode(callSuper = false)
public class ClaimEncounterCopyDiagnosticOrderTestReport
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_encounter_copy_id", nullable = false)
    private Long claimEncounterCopyId;

    @Column(name = "diagnostic_order_test_report_id")
    private Long diagnosticOrderTestReportId;

    @Column(name = "order_test_id", nullable = false)
    private Long orderTestId;

    @Column(name = "report", columnDefinition = "text")
    private String report;

    @Column(name = "radiologist_information", columnDefinition = "text")
    private String radiologistInformation;

    @Column(name = "critical_findings", columnDefinition = "text")
    private String criticalFindings;

    @Column(name = "radiologist_comments", columnDefinition = "text")
    private String radiologistComments;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 50)
    private Severity severity;

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

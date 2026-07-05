package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;
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
@Table(
        name = "diagnostic_order_tests_report")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class DiagnosticOrderTestReport extends AbstractAuditingEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "order_test_id", nullable = false)
    private Long orderTestId;

    @Column(name = "report", columnDefinition = "text")
    private String report;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 50)
    private Severity severity;

    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_date")
    private Instant approvedDate;

    @Column(name = "rejected_by", length = 50)
    private String rejectedBy;

    @Column(name = "rejected_date")
    private Instant rejectedDate;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    @Column(name = "review_by", length = 50)
    private String reviewBy;

    @Column(name = "review_date")
    private Instant reviewDate;

    @Column(name = "second_approved_by")
    private String secondApprovedBy;

    @Column(name = "second_approved_date")
    private Instant secondApprovedDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 50)
    private DiagnosticStatus processingStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "image_status", length = 50)
    private RadiologyImageStatus imageStatus;
}

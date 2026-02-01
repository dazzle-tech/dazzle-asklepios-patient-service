package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_test_id", nullable = false)
    private Long orderTestId;

    @Column(name = "report", columnDefinition = "text")
    private String report;

    @Column(name = "severity", length = 50)
    private String severity;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 50)
    private DiagnosticStatus processingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_status", length = 50)
    private RadiologyImageStatus imageStatus;
}

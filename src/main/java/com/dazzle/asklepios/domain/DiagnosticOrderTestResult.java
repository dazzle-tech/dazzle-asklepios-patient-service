
package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestResultType;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_order_tests_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class DiagnosticOrderTestResult extends AbstractAuditingEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "order_test_id", nullable = false)
    private Long orderTestId;

    @Column(name = "profile_test_id", nullable = false)
    private Long profileTestId;

    @Column(name = "result_value_number", precision = 19, scale = 2)
    private BigDecimal resultValueNumber;

    @Column(name = "result_value_text", columnDefinition = "text")
    private String resultValueText;

    @Enumerated(EnumType.STRING)
    @Column(name = "marker", length = 50)
    private TestResultMarker marker;

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 50)
    private DiagnosticStatus processingStatus=DiagnosticStatus.NEW;

    @Column(name = "normal_range_value", length = 150)
    private String normalRangeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type_at_entry", length = 20)
    private TestResultType resultTypeAtEntry;
}

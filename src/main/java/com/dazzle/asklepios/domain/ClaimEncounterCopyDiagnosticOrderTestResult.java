package com.dazzle.asklepios.domain;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "claim_encounter_copy_diagnostic_order_test_results")
@EqualsAndHashCode(callSuper = false)
public class ClaimEncounterCopyDiagnosticOrderTestResult
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_encounter_copy_id", nullable = false)
    private Long claimEncounterCopyId;

    @Column(name = "diagnostic_order_test_result_id")
    private Long diagnosticOrderTestResultId;

    @Column(name = "order_test_id", nullable = false)
    private Long orderTestId;

    @Column(name = "profile_test_id")
    private Long profileTestId;

    @Column(name = "result_value_number", precision = 19, scale = 2)
    private BigDecimal resultValueNumber;

    @Column(name = "result_value_text", columnDefinition = "text")
    private String resultValueText;

    @Enumerated(EnumType.STRING)
    @Column(name = "marker", length = 50)
    private TestResultMarker marker;

    @Column(name = "normal_range_value", length = 150)
    private String normalRangeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type_at_entry", length = 20)
    private TestResultType resultTypeAtEntry;

}

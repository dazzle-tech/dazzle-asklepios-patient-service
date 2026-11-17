package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DentalChartInventoryAction;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * One tooth-level change of a saved inventory batch. Rows of one batch share {@code batchId} and
 * {@code reason}; actor and time come from the auditing columns.
 */
@Entity
@Table(name = "dental_chart_inventory_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentalChartInventoryAudit extends AbstractAuditingEntity<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "chart_id", nullable = false)
    private Long chartId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotNull
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private DentalChartInventoryAction action;

    @NotNull
    @Column(name = "tooth_number", nullable = false, length = 10)
    private String toothNumber;

    @Column(name = "related_tooth_number", length = 10)
    private String relatedToothNumber;

    // Tooth inventory status, or the chart type for a CHART_TYPE row.
    @Column(name = "status_before", length = 20)
    private String statusBefore;

    @NotNull
    @Column(name = "status_after", nullable = false, length = 20)
    private String statusAfter;

    @NotNull
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "reopen_session_id")
    private Long reopenSessionId;
}

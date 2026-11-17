package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalChartInventoryAudit;
import com.dazzle.asklepios.domain.enumeration.DentalChartInventoryAction;

import java.io.Serializable;
import java.time.Instant;

public record ChartInventoryAuditDTO(
        Long id,
        String batchId,
        DentalChartInventoryAction action,
        String toothNumber,
        String relatedToothNumber,
        String statusBefore,
        String statusAfter,
        String reason,
        Long encounterId,
        Long reopenSessionId,
        String changedBy,
        Instant changedAt
) implements Serializable {

    public static ChartInventoryAuditDTO fromEntity(DentalChartInventoryAudit entity) {
        return new ChartInventoryAuditDTO(
                entity.getId(),
                entity.getBatchId(),
                entity.getAction(),
                entity.getToothNumber(),
                entity.getRelatedToothNumber(),
                entity.getStatusBefore(),
                entity.getStatusAfter(),
                entity.getReason(),
                entity.getEncounterId(),
                entity.getReopenSessionId(),
                entity.getCreatedBy(),
                entity.getCreatedDate()
        );
    }
}

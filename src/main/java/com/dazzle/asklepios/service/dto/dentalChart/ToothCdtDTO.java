package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.ToothCdt;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;

import java.io.Serializable;
import java.time.Instant;

public record ToothCdtDTO(
        Long id,
        Long toothId,
        Long cdtCodeId,
        ServiceSource source,
        Long toothActionId,
        ToothSurface surface,
        boolean cancelled,
        String cancelledBy,
        Instant cancelledDate,
        String cancellationReason
) implements Serializable {

    public static ToothCdtDTO fromEntity(ToothCdt entity) {
        return new ToothCdtDTO(
                entity.getId(),
                entity.getToothId(),
                entity.getCdtCodeId(),
                entity.getSource(),
                entity.getToothActionId(),
                entity.getSurface(),
                entity.isCancelled(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason()
        );
    }
}

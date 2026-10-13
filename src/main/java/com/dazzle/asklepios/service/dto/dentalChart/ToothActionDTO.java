package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;

import java.io.Serializable;
import java.time.Instant;

public record ToothActionDTO(
        Long id,
        Long toothId,
        Long dentalActionId,
        String note,
        ToothSurface surface,
        boolean existing,
        String imageName,
        String toothNumber,
        String anesthesiaUsed,
        String dose,
        boolean cancelled,
        String cancelledBy,
        Instant cancelledDate,
        String cancellationReason
) implements Serializable {

    public static ToothActionDTO fromEntity(ToothAction entity) {
        return new ToothActionDTO(
                entity.getId(),
                entity.getToothId(),
                entity.getDentalActionId(),
                entity.getNote(),
                entity.getSurface(),
                entity.isExisting(),
                entity.getImageName(),
                entity.getToothNumber(),
                entity.getAnesthesiaUsed(),
                entity.getDose(),
                entity.isCancelled(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason()
        );
    }
}

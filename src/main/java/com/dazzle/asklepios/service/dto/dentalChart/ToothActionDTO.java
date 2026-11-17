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
        Integer visitNumber,
        String imageName,
        String toothNumber,
        String anesthesiaUsed,
        String dose,
        boolean cancelled,
        String cancelledBy,
        Instant cancelledDate,
        String cancellationReason,
        Long carriedFromActionId,
        String originalRecordedBy,
        Instant originalRecordedAt
) implements Serializable {

    public static ToothActionDTO fromEntity(ToothAction entity) {
        return fromEntity(entity, null);
    }

    /** {@code origin} is the original action a carried-forward finding was copied from (may be null). */
    public static ToothActionDTO fromEntity(ToothAction entity, ToothAction origin) {
        return new ToothActionDTO(
                entity.getId(),
                entity.getToothId(),
                entity.getDentalActionId(),
                entity.getNote(),
                entity.getSurface(),
                entity.isExisting(),
                entity.getVisitNumber(),
                entity.getImageName(),
                entity.getToothNumber(),
                entity.getAnesthesiaUsed(),
                entity.getDose(),
                entity.isCancelled(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason(),
                entity.getCarriedFromActionId(),
                origin == null ? null : origin.getCreatedBy(),
                origin == null ? null : origin.getCreatedDate()
        );
    }
}

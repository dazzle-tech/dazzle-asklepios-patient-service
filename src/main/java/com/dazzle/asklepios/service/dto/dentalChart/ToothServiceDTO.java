package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.ToothService;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record ToothServiceDTO(
        Long id,
        Long toothId,
        Long serviceId,
        ServiceSource source,
        Long toothActionId,
        BigDecimal price,
        boolean cancelled,
        String cancelledBy,
        Instant cancelledDate,
        String cancellationReason
) implements Serializable {

    public static ToothServiceDTO fromEntity(ToothService entity) {
        return new ToothServiceDTO(
                entity.getId(),
                entity.getToothId(),
                entity.getServiceId(),
                entity.getSource(),
                entity.getToothActionId(),
                entity.getPrice(),
                entity.isCancelled(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason()
        );
    }
}

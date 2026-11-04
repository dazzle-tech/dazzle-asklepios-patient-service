package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.ToothActionLog;
import com.dazzle.asklepios.domain.enumeration.ToothActionLogType;

import java.io.Serializable;
import java.time.Instant;

public record ToothActionLogDTO(
        Long id,
        Long toothId,
        Long dentalActionId,
        ToothActionLogType logType,
        Instant logTime,
        String logOwner
) implements Serializable {

    public static ToothActionLogDTO fromEntity(ToothActionLog entity) {
        return new ToothActionLogDTO(
                entity.getId(),
                entity.getToothId(),
                entity.getDentalActionId(),
                entity.getLogType(),
                entity.getLogTime(),
                entity.getLogOwner()
        );
    }
}

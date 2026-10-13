package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalChartProgressNote;

import java.io.Serializable;
import java.time.Instant;

public record ProgressNoteDTO(
        Long id,
        Long chartId,
        String note,
        String createdBy,
        Instant createdDate
) implements Serializable {

    public static ProgressNoteDTO fromEntity(DentalChartProgressNote entity) {
        return new ProgressNoteDTO(
                entity.getId(),
                entity.getChartId(),
                entity.getNote(),
                entity.getCreatedBy(),
                entity.getCreatedDate()
        );
    }
}

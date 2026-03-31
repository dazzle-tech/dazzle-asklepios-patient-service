package com.dazzle.asklepios.web.rest.vm.availabilityGenerationBatch;

import com.dazzle.asklepios.domain.enumeration.BatchStatus;

import java.time.Instant;
import java.time.LocalDate;

public record ApplyAvailabilityTemplateResponseDTO(
        Long batchId,
        Long templateId,
        Integer totalSlots,
        Integer dailyAvg,
        BatchStatus executionStatus,
        String message
) {
}

package com.dazzle.asklepios.web.rest.vm.availabilityGenerationBatch;

import com.dazzle.asklepios.domain.enumeration.AvailabilityGenerationScope;
import com.dazzle.asklepios.domain.enumeration.BatchStatus;
import com.dazzle.asklepios.domain.enumeration.HolidayHandlingMode;

import java.time.Instant;

public record ApplyAvailabilityTemplateResponseVM(
        Long batchId,
        Long templateId,
        AvailabilityGenerationScope scope,
        Instant applyStartDateTime,
        Instant applyEndDateTime,
        Integer totalSlots,
        Integer dailyAvg,
        BatchStatus executionStatus,
        String message,
        HolidayHandlingMode holidayHandlingMode
) {
}

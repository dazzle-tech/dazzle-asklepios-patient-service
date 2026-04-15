package com.dazzle.asklepios.web.rest.vm.availabilityTemplate;

import com.dazzle.asklepios.domain.AvailabilityTemplateIntervalBreak;

import java.io.Serializable;
import java.time.LocalTime;

public record AvailabilityTemplateIntervalBreakResponseVM(
        Long id,
        Long intervalId,
        Long templateId,
        LocalTime startTime,
        LocalTime endTime
) implements Serializable {

    public static AvailabilityTemplateIntervalBreakResponseVM of(AvailabilityTemplateIntervalBreak entity) {
        return new AvailabilityTemplateIntervalBreakResponseVM(
                entity.getId(),
                entity.getInterval().getId(),
                entity.getInterval().getTemplate().getId(),
                entity.getStartTime(),
                entity.getEndTime()
        );
    }
}
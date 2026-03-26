package com.dazzle.asklepios.web.rest.vm.availabilityTemplate;

import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.domain.enumeration.SlotStrategy;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityTemplateIntervalResponseVM(
        Long id,
        Long templateId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        SlotStrategy slotStrategy,
        Integer slotDurationMinutes,
        List<AvailabilityTemplateAllowedServiceResponseVM> allowedServices
) implements Serializable {

    public static AvailabilityTemplateIntervalResponseVM ofEntity(AvailabilityTemplateInterval entity) {
        return new AvailabilityTemplateIntervalResponseVM(
                entity.getId(),
                entity.getTemplate() != null ? entity.getTemplate().getId() : null,
                entity.getDayOfWeek(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getSlotStrategy(),
                entity.getSlotDurationMinutes(),
                entity.getAllowedServices() != null
                        ? entity.getAllowedServices().stream()
                        .map(AvailabilityTemplateAllowedServiceResponseVM::ofEntity)
                        .toList()
                        : List.of()
        );
    }
}
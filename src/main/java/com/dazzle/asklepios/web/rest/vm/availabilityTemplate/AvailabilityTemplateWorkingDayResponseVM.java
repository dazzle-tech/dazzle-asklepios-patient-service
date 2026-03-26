package com.dazzle.asklepios.web.rest.vm.availabilityTemplate;

import com.dazzle.asklepios.domain.AvailabilityTemplateWorkingDay;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;

public record AvailabilityTemplateWorkingDayResponseVM(
         DayOfWeek dayOfWeek,
         Boolean isWorking
) {
    public static AvailabilityTemplateWorkingDayResponseVM ofEntity(AvailabilityTemplateWorkingDay entity) {
        if (entity == null) return null;
        return new AvailabilityTemplateWorkingDayResponseVM(
                entity.getDayOfWeek(),
                entity.getIsWorking()
        );
    }
}
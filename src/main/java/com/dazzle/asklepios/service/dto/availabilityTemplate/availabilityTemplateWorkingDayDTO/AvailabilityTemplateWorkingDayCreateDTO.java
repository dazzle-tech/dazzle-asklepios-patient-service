package com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateWorkingDayDTO;

import com.dazzle.asklepios.domain.enumeration.DayOfWeek;

public record AvailabilityTemplateWorkingDayCreateDTO(
        DayOfWeek dayOfWeek,
        Boolean isWorking
) {}

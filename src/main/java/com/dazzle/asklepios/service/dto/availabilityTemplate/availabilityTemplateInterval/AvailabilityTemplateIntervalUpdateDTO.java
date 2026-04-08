package com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateInterval;

import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.domain.enumeration.SlotStrategy;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateAllowedServices.AvailabilityTemplateAllowedServiceDTO;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityTemplateIntervalUpdateDTO(

        @NotNull
        Long id,
        DayOfWeek dayOfWeek,

        LocalTime startTime,

        LocalTime endTime,

        SlotStrategy slotStrategy,

        Integer slotDurationMinutes,

        List<AvailabilityTemplateAllowedServiceDTO> allowedServices

) implements Serializable {}
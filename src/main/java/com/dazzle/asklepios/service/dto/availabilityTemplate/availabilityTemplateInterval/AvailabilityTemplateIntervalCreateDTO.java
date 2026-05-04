package com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateInterval;

import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.domain.enumeration.SlotStrategy;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateAllowedServices.AvailabilityTemplateAllowedServiceDTO;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityTemplateIntervalCreateDTO(

        @NotNull
        Long templateId,

        @NotNull
        DayOfWeek dayOfWeek,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime,

        @NotNull
        SlotStrategy slotStrategy,

        @NotNull
        Integer slotDurationMinutes,

        Boolean applyToAllWorkingDays,

        List<AvailabilityTemplateAllowedServiceDTO> allowedServices

) implements Serializable {}

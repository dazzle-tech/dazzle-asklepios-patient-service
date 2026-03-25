package com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateAllowedServices;

import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record AvailabilityTemplateAllowedServiceDTO(

        Long id,

        @NotNull
        EncounterReason service

) implements Serializable {}
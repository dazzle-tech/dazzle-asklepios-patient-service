package com.dazzle.asklepios.service.dto.additionalMeasurements;

import com.dazzle.asklepios.domain.enumeration.AgeGroupType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdditionalMeasurementsInfantCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull AgeGroupType ageGroup,

        @NotBlank String hearingTest,

        @NotNull  Boolean dehydration,
        @NotNull Boolean nasalFlaring,
        @NotNull Boolean responseToLight,
        @NotNull Boolean pupilResponse,
        @NotNull Boolean abilityToFollowTarget,
        @NotNull Boolean colorTesting,

        @NotNull Boolean isActive
) implements Serializable {}

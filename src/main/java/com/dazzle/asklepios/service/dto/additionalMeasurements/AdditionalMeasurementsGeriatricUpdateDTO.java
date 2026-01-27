// AdditionalMeasurementsGeriatricUpdateDTO.java
package com.dazzle.asklepios.service.dto.additionalMeasurements;

import com.dazzle.asklepios.domain.enumeration.AgeGroupType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdditionalMeasurementsGeriatricUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull AgeGroupType ageGroup,

        @NotNull Boolean fallRisk,
        @NotNull Boolean visionProblemsAffectingFunction,
        @NotNull Boolean hearingProblemsAffectingFunction,

        String details,

        @NotBlank String actionToTake,

        @NotNull Boolean isActive
) implements Serializable {}

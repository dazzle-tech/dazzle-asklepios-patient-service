package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.enumeration.AllergenTypes;
import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record PatientAllergiesUpdateDTO(
        @NotNull Long id,
        @NotNull AllergenTypes allergenType,
        Long allergenId,
        @NotNull Severity severity,
        Long medicationClassId,
        String criticality,
        String certainty,
        String treatmentStrategy,
        String onset,
        Boolean onsetDateUndefined,
        Instant onsetDate,
        String typeOfPropensity,
        Boolean byPatient,
        String sourceOfInformation,
        String note,
        String allergicReactions,
        List<Long> activeIngredients
) implements Serializable {}

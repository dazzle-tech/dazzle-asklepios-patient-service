package com.dazzle.asklepios.service.dto;


import com.dazzle.asklepios.domain.enumeration.AllergenTypes;
import com.dazzle.asklepios.domain.enumeration.PatientAllergyStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record PatientAllergiesCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull AllergenTypes allergenType,
        Long allergenId,
        @NotNull Severity severity,
        Long medicationClassId,
        String criticality,
        String certainty,
        String treatmentStrategy,
        String onset,
        boolean onsetDateUndefined,
        Instant onsetDate,
        String typeOfPropensity,
        boolean byPatient,
        String sourceOfInformation,
        String note,
        String allergicReactions,
        @NotNull PatientAllergyStatus status,
        @NotNull String resolvedBy,
        Instant resolvedDate,
        @NotNull String cancelledBy,
        Instant cancelledDate,
        String cancellationReason,
        List<Long> activeIngredients
) implements Serializable {}

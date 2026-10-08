package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotNull;

import java.util.Date;

public record ClaimEncounterCopySocialHistoryCreateDTO(
        Boolean isCurrentSmoker,
        Date smokeStartDate,
        Integer cigaretteAmount,
        String cigaretteType,
        Boolean isPreviousSmoker,
        Date smokeQuitDate,
        Boolean exposureToSecondHandSmoke,
        Boolean alcoholConsumption,
        String typeOfAlcohol,
        Date alcoholSinceWhen,
        Boolean substanceUse,
        String route,
        String frequency,
        String physicalLimitation,
        String diagnosedEatingDisorders,
        @NotNull Boolean patientIsFree,
        String freeText
) {}
package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;

import java.time.Instant;
import java.util.Date;

public record ClaimEncounterCopySocialHistoryResponse(
        Long id,
        Long socialHistoryId,
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
        Boolean patientIsFree,
        String freeText,
        PatientHistoryStatus status,
        String cancelledBy,
        Instant cancelledDate,
        String cancellationReason
) {}
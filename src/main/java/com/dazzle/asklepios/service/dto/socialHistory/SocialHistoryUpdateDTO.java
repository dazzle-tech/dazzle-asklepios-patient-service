package com.dazzle.asklepios.service.dto.socialHistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SocialHistoryUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

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

        @NotNull
        Boolean patientIsFree,

        String freeText

) implements Serializable {
}
package com.dazzle.asklepios.service.dto.socialHistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SocialHistoryUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Boolean isCurrentSmoker,

        @PastOrPresent
        Date smokeStartDate,
        Integer cigaretteAmount,
        String cigaretteType,

        @NotNull
        Boolean isPreviousSmoker,

        @PastOrPresent
        Date smokeQuitDate,

        @NotNull
        Boolean exposureToSecondHandSmoke,

        @NotNull
        Boolean alcoholConsumption,
        String typeOfAlcohol,

        @PastOrPresent
        Date alcoholSinceWhen,

        @NotNull
        Boolean substanceUse,

        String route,
        String frequency,

        String physicalLimitation,
        String diagnosedEatingDisorders

) implements Serializable {
}

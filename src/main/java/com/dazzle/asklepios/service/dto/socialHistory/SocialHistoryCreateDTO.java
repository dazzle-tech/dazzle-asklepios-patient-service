package com.dazzle.asklepios.service.dto.socialHistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SocialHistoryCreateDTO(

        @NotNull
        Long patientId,

        Boolean isCurrentSmoker,

        @PastOrPresent
        Date smokeStartDate,
        Integer cigaretteAmount,
        String cigaretteType,

        Boolean isPreviousSmoker,
        @PastOrPresent
        Date smokeQuitDate,

        Boolean exposureToSecondHandSmoke,

        Boolean alcoholConsumption,
        String typeOfAlcohol,
        @PastOrPresent
        Date alcoholSinceWhen,

        Boolean substanceUse,
        String route,
        String frequency,

        String physicalLimitation,
        String diagnosedEatingDisorders

) implements Serializable {
}

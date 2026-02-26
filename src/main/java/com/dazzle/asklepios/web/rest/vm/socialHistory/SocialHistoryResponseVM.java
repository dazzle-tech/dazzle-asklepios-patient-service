package com.dazzle.asklepios.web.rest.vm.socialHistory;

import com.dazzle.asklepios.domain.SocialHistory;

import java.time.Instant;
import java.util.Date;

public record SocialHistoryResponseVM(
        Long id,
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

        String createdBy,
        Instant createdDate,

        String lastModifiedBy,
        Instant lastModifiedDate
) {
    public static SocialHistoryResponseVM ofEntity(SocialHistory entity) {
        return new SocialHistoryResponseVM(
                entity.getId(),
                entity.getPatient().getId(),

                entity.getIsCurrentSmoker(),
                entity.getSmokeStartDate(),
                entity.getCigaretteAmount(),
                entity.getCigaretteType(),

                entity.getIsPreviousSmoker(),
                entity.getSmokeQuitDate(),

                entity.getExposureToSecondHandSmoke(),

                entity.getAlcoholConsumption(),
                entity.getTypeOfAlcohol(),
                entity.getAlcoholSinceWhen(),

                entity.getSubstanceUse(),
                entity.getRoute(),
                entity.getFrequency(),

                entity.getPhysicalLimitation(),
                entity.getDiagnosedEatingDisorders(),

                entity.getCreatedBy(),
                entity.getCreatedDate(),

                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}

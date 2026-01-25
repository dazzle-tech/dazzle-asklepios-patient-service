package com.dazzle.asklepios.service.dto.generalAssessment;

import com.dazzle.asklepios.domain.GeneralAssessment;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeneralAssessmentUpdateDTO(
        @NotNull Long id,
        String positionStatus,
        String bodyMovements,
        String levelOfConsciousness,
        String facialExpression,
        String speech,
        String moodBehavior,
        Boolean memoryRemote,
        Boolean memoryRecent,
        Boolean signsOfAgitation,
        Boolean signsOfDepression,
        Boolean signsOfSuicidalIdeation,
        Boolean signsOfSubstanceUse,
        Boolean isTriage
) implements Serializable {

    public static GeneralAssessmentUpdateDTO ofEntity(GeneralAssessment entity) {
        return new GeneralAssessmentUpdateDTO(
                entity.getId(),
                entity.getPositionStatus(),
                entity.getBodyMovements(),
                entity.getLevelOfConsciousness(),
                entity.getFacialExpression(),
                entity.getSpeech(),
                entity.getMoodBehavior(),
                entity.getMemoryRemote(),
                entity.getMemoryRecent(),
                entity.getSignsOfAgitation(),
                entity.getSignsOfDepression(),
                entity.getSignsOfSuicidalIdeation(),
                entity.getSignsOfSubstanceUse(),
                entity.getIsTriage()
        );
    }
}
package com.dazzle.asklepios.service.dto.generalAssessment;

import com.dazzle.asklepios.domain.GeneralAssessment;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeneralAssessmentCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotNull String positionStatus,
        @NotNull String bodyMovements,
        @NotNull String levelOfConsciousness,
        @NotNull String facialExpression,
        @NotNull String speech,
        @NotNull String moodBehavior,
        Boolean memoryRemote,
        Boolean memoryRecent,
        Boolean signsOfAgitation,
        Boolean signsOfDepression,
        Boolean signsOfSuicidalIdeation,
        Boolean signsOfSubstanceUse,
        @NotNull Boolean isTriage
) implements Serializable {

    public static GeneralAssessmentCreateDTO ofEntity(GeneralAssessment entity) {
        return new GeneralAssessmentCreateDTO(
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getEncounterId(),
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
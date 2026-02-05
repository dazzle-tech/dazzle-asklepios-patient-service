package com.dazzle.asklepios.service.dto.emergencyTriage;

import com.dazzle.asklepios.domain.EmergencyTriage;
import com.dazzle.asklepios.domain.enumeration.AVPUScale;
import com.dazzle.asklepios.domain.enumeration.PainLevel;
import com.dazzle.asklepios.domain.enumeration.YesNoQuestion;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmergencyTriageLevelAssessmentUpdateDTO(
        @NotNull Long id,
        @NotNull YesNoQuestion lifeSaving,
        YesNoQuestion unresponsive,
        YesNoQuestion highRisk,
        AVPUScale avpuScale,
        PainLevel painScore,
        YesNoQuestion labsRequired,
        YesNoQuestion imagingRequired,
        YesNoQuestion ivFluidsRequired,
        YesNoQuestion medicationRequired,
        YesNoQuestion ecgRequired,
        YesNoQuestion consultationRequired
) implements Serializable {

    public static EmergencyTriageLevelAssessmentUpdateDTO ofEntity(EmergencyTriage entity) {
        return new EmergencyTriageLevelAssessmentUpdateDTO(
                entity.getId(),
                entity.getLifeSaving(),
                entity.getUnresponsive(),
                entity.getHighRisk(),
                entity.getAvpuScale(),
                entity.getPainScore(),
                entity.getLabsRequired(),
                entity.getImagingRequired(),
                entity.getIvFluidsRequired(),
                entity.getMedicationRequired(),
                entity.getEcgRequired(),
                entity.getConsultationRequired()
        );
    }
}
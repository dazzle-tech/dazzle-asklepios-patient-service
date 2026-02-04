package com.dazzle.asklepios.service.dto.emergencyTriage;

import com.dazzle.asklepios.domain.EmergencyTriage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmergencyTriageUpdateDTO(
        @NotNull Long id,
        @NotNull Boolean rightEyeLightResponse,
        String rightEyePupilSize,
        @NotNull Boolean leftEyeLightResponse,
        String leftEyePupilSize,
        String hpiAdditionalNotes
) implements Serializable {

    public static EmergencyTriageUpdateDTO ofEntity(EmergencyTriage entity) {
        return new EmergencyTriageUpdateDTO(
                entity.getId(),
                entity.getRightEyeLightResponse(),
                entity.getRightEyePupilSize(),
                entity.getLeftEyeLightResponse(),
                entity.getLeftEyePupilSize(),
                entity.getHpiAdditionalNotes()
        );
    }
}
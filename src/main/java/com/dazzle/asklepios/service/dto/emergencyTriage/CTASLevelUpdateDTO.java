package com.dazzle.asklepios.service.dto.emergencyTriage;

import com.dazzle.asklepios.domain.EmergencyTriage;
import com.dazzle.asklepios.domain.enumeration.AVPUScale;
import com.dazzle.asklepios.domain.enumeration.EmergencyLevel;
import com.dazzle.asklepios.domain.enumeration.PainLevel;
import com.dazzle.asklepios.domain.enumeration.YesNoQuestion;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CTASLevelUpdateDTO(
        @NotNull Long id,
        EmergencyLevel emergencyLevel
) implements Serializable {

    public static CTASLevelUpdateDTO ofEntity(EmergencyTriage entity) {
        return new CTASLevelUpdateDTO(
                entity.getId(),
                entity.getEmergencyLevel()
        );
    }
}
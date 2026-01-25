package com.dazzle.asklepios.service.dto.emergencyTriage;

import com.dazzle.asklepios.domain.EmergencyTriage;
import com.dazzle.asklepios.domain.enumeration.TriageDestination;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmergencyTriageDestinationUpdateDTO(
        @NotNull Long id,
        @NotNull TriageDestination destination
) implements Serializable {

    public static EmergencyTriageDestinationUpdateDTO ofEntity(EmergencyTriage entity) {
        return new EmergencyTriageDestinationUpdateDTO(
                entity.getId(),
                entity.getDestination()
        );
    }
}
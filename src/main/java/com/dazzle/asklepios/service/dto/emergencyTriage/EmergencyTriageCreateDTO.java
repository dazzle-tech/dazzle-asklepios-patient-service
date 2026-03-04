package com.dazzle.asklepios.service.dto.emergencyTriage;

import com.dazzle.asklepios.domain.EmergencyTriage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmergencyTriageCreateDTO(
        @NotNull Long patientId,
        @NotNull Long encounterId
) implements Serializable {

    public static EmergencyTriageCreateDTO ofEntity(EmergencyTriage entity) {
        return new EmergencyTriageCreateDTO(
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getEncounter() != null ? entity.getEncounter().getId() : null
        );
    }
}
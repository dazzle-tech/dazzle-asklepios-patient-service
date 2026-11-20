package com.dazzle.asklepios.service.dto.patientEncounter;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record EncounterInvestigationReasonDTO(

        @NotBlank
        String investigationNotPerformedReason

) implements Serializable {
}

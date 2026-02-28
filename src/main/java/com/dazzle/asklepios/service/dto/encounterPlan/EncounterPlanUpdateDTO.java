package com.dazzle.asklepios.service.dto.encounterPlan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterPlanUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotBlank
        String planInstructions

) implements Serializable {
}

package com.dazzle.asklepios.service.dto.encounterAssessment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterAssessmentCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotBlank
        String assessment

) implements Serializable {
}

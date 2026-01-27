package com.dazzle.asklepios.service.dto.painAssessment;

import com.dazzle.asklepios.domain.enumeration.PainLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PainAssessmentCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        String painDegree,

        @NotNull
        PainLevel painLevel,

        String painDescription,

        @NotNull
        Boolean isActive

) implements Serializable {}

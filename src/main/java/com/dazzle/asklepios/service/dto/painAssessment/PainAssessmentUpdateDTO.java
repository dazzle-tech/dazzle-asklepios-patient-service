package com.dazzle.asklepios.service.dto.painAssessment;

import com.dazzle.asklepios.domain.enumeration.PainLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PainAssessmentUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        PainLevel painLevel,

        String painDescription,

        String painPattern,

        @NotNull
        Boolean isActive

) implements Serializable {}

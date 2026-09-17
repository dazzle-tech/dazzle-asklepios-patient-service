package com.dazzle.asklepios.service.dto.painAssessment;

import com.dazzle.asklepios.domain.enumeration.PainAssessmentTypes;
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
        PainLevel painLevel,

        @NotNull
        PainAssessmentTypes painAssessmentType,

        String painDescription,

        String painPattern,

        @NotNull
        Boolean isActive

) implements Serializable {}

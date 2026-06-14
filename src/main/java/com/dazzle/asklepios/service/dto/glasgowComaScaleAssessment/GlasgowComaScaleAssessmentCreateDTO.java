package com.dazzle.asklepios.service.dto.glasgowComaScaleAssessment;

import com.dazzle.asklepios.domain.enumeration.GCSEye;
import com.dazzle.asklepios.domain.enumeration.GCSMotor;
import com.dazzle.asklepios.domain.enumeration.GCSVerbal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GlasgowComaScaleAssessmentCreateDTO(

        @NotNull
        Long encounterId,

        @NotNull
        Long patientId,

        @NotNull
        GCSEye eyeOpening,

        @NotNull
        GCSVerbal verbalResponse,

        @NotNull
        GCSMotor motorResponse

) implements Serializable {
}
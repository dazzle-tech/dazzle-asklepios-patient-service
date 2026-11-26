package com.dazzle.asklepios.service.dto.randomBloodSugar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RandomBloodSugarCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        String randomBloodGlucose,

        Boolean isActive

) implements Serializable {
}

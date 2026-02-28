package com.dazzle.asklepios.service.dto.bodyMeasurements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BodyMeasurementsUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        BigDecimal weight,

        @NotNull
        BigDecimal height,
        BigDecimal headCircumference,

        @NotNull
        Boolean isActive

) implements Serializable {
}

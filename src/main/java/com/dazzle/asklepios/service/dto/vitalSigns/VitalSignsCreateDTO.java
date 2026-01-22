package com.dazzle.asklepios.service.dto.vitalSigns;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VitalSignsCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        Long encounterId,

        @NotNull
        Integer bloodPressureSystolic,

        @NotNull
        Integer bloodPressureDiastolic,

        @NotNull
        BigDecimal temperature,

        String measurementSite,
        Integer heartRate,
        BigDecimal oxygenSaturation,
        Integer respiratoryRate,

        Boolean isTriage,

        String notes

) implements Serializable {
}

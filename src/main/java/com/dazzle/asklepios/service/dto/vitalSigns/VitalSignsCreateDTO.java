package com.dazzle.asklepios.service.dto.vitalSigns;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
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

        @NotNull
        Boolean isTriage,

        @NotNull
        Boolean isActive,

        String notes

) implements Serializable {

        @AssertTrue(message = "measurementSite, heartRate, oxygenSaturation, and respiratoryRate are required when isTriage is true")
        public boolean isTriageFieldsValid() {

                if (isTriage == null || !isTriage) {
                        return true;
                }

                return measurementSite != null && !measurementSite.isBlank()
                        && heartRate != null
                        && oxygenSaturation != null
                        && respiratoryRate != null;
        }
}
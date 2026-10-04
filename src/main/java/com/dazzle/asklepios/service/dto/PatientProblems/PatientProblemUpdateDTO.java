package com.dazzle.asklepios.service.dto.PatientProblems;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientProblemUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        String condition,
        Date dateOfDiagnosis,
        String conditionStatus,
        String type,
        Date dateOfResolution,
        Boolean byPatient,
        String sourceOfInformation,
        @NotNull Boolean patientIsFree,
        String freeText
) implements Serializable {
}
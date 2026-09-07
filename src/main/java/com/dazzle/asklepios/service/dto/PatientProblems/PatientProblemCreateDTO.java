package com.dazzle.asklepios.service.dto.PatientProblems;

import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientProblemCreateDTO(
        @NotNull Long patientId,
         String condition,
        Date dateOfDiagnosis,
        String conditionStatus,
         String type,
        Date dateOfResolution,
         Boolean byPatient,
        String sourceOfInformation,
        Boolean patientIsFree
) implements Serializable {
}
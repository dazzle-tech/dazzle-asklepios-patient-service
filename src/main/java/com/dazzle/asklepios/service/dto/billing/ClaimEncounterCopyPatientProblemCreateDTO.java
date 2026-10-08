package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.NotNull;

import java.util.Date;

public record ClaimEncounterCopyPatientProblemCreateDTO(
        String condition,
        Date dateOfDiagnosis,
        String conditionStatus,
        String type,
        Date dateOfResolution,
        Boolean byPatient,
        String sourceOfInformation,
        @NotNull Boolean patientIsFree,
        String freeText
) {}
package com.dazzle.asklepios.service.dto.patientAdministrativeWarnings;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record PatientAdministrativeWarningsCreateDTO(
        @NotNull Long patientId,
        @NotBlank String warningType,
        String description
) implements Serializable {

    public static PatientAdministrativeWarningsCreateDTO ofEntity(PatientAdministrativeWarnings patientAdministrativeWarnings) {
        return new PatientAdministrativeWarningsCreateDTO(
                patientAdministrativeWarnings.getPatient() != null ? patientAdministrativeWarnings.getPatient().getId() : null,
                patientAdministrativeWarnings.getWarningType(),
                patientAdministrativeWarnings.getDescription()
        );
    }
}

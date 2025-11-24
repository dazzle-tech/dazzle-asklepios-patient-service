package com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record PatientAdministrativeWarningsCreateVM(
        @NotNull Long patientId,
        @NotBlank String warningType,
        String description
) implements Serializable {

    public static PatientAdministrativeWarningsCreateVM ofEntity(PatientAdministrativeWarnings patientAdministrativeWarnings) {
        return new PatientAdministrativeWarningsCreateVM(
                patientAdministrativeWarnings.getPatient() != null ? patientAdministrativeWarnings.getPatient().getId() : null,
                patientAdministrativeWarnings.getWarningType(),
                patientAdministrativeWarnings.getDescription()
        );
    }
}

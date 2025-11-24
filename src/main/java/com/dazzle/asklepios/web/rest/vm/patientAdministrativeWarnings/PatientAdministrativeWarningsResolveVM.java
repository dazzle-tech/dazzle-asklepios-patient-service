package com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record PatientAdministrativeWarningsResolveVM(
        @NotNull Long id,
        @NotNull Boolean resolved,
        String resolvedBy,
        Instant resolvedDate
) implements Serializable {
    public static PatientAdministrativeWarningsResolveVM ofEntity(PatientAdministrativeWarnings patientAdministrativeWarnings) {
        return new PatientAdministrativeWarningsResolveVM(
                patientAdministrativeWarnings.getId(),
                patientAdministrativeWarnings.getResolved(),
                patientAdministrativeWarnings.getResolvedBy(),
                patientAdministrativeWarnings.getResolvedDate()
        );
    }
}

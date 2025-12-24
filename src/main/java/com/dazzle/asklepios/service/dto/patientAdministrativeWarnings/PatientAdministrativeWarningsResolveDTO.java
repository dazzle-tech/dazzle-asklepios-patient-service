package com.dazzle.asklepios.service.dto.patientAdministrativeWarnings;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.Instant;

public record PatientAdministrativeWarningsResolveDTO(
        @NotNull Long id,
        @NotNull Boolean resolved,
        String resolvedBy,
        Instant resolvedDate
) implements Serializable {
    public static PatientAdministrativeWarningsResolveDTO ofEntity(PatientAdministrativeWarnings patientAdministrativeWarnings) {
        return new PatientAdministrativeWarningsResolveDTO(
                patientAdministrativeWarnings.getId(),
                patientAdministrativeWarnings.getResolved(),
                patientAdministrativeWarnings.getResolvedBy(),
                patientAdministrativeWarnings.getResolvedDate()
        );
    }
}

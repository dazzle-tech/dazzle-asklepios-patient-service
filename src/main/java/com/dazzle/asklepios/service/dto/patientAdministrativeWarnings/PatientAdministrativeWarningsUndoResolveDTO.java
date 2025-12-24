package com.dazzle.asklepios.service.dto.patientAdministrativeWarnings;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PatientAdministrativeWarningsUndoResolveDTO(
        @NotNull Long id,
        @NotNull Boolean resolved,
        String undoResolvedBy,
        Instant undoResolvedDate

) {
    public static PatientAdministrativeWarningsUndoResolveDTO ofEntity(PatientAdministrativeWarnings patientAdministrativeWarnings) {
        return new PatientAdministrativeWarningsUndoResolveDTO(
                patientAdministrativeWarnings.getId(),
                patientAdministrativeWarnings.getResolved(),
                patientAdministrativeWarnings.getUndoResolvedBy(),
                patientAdministrativeWarnings.getUndoResolvedDate()
        );
    }
}


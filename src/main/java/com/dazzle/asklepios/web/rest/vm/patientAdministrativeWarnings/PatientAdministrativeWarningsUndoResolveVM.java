package com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PatientAdministrativeWarningsUndoResolveVM(
        @NotNull Long id,
        @NotNull Boolean resolved,
        String undoResolvedBy,
        Instant undoResolvedDate

) {
    public static PatientAdministrativeWarningsUndoResolveVM ofEntity(PatientAdministrativeWarnings patientAdministrativeWarnings) {
        return new PatientAdministrativeWarningsUndoResolveVM(
                patientAdministrativeWarnings.getId(),
                patientAdministrativeWarnings.getResolved(),
                patientAdministrativeWarnings.getUndoResolvedBy(),
                patientAdministrativeWarnings.getUndoResolvedDate()
        );
    }
}


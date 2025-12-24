package com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;

import java.io.Serializable;
import java.time.Instant;

public record PatientAdministrativeWarningsResponseVM(
        Long id,
        Long patientId,
        String warningType,
        String description,
        Boolean resolved,
        String resolvedBy,
        Instant resolvedDate,
        String undoResolvedBy,
        Instant undoResolvedDate

) implements Serializable {
    public static PatientAdministrativeWarningsResponseVM ofEntity(PatientAdministrativeWarnings entity) {
        return new PatientAdministrativeWarningsResponseVM(
                entity.getId(),
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getWarningType(),
                entity.getDescription(),
                entity.getResolved(),
                entity.getResolvedBy(),
                entity.getResolvedDate(),
                entity.getUndoResolvedBy(),
                entity.getUndoResolvedDate()
        );
    }
}

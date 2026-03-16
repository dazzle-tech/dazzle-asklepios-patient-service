package com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings;

import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import com.dazzle.asklepios.web.rest.vm.patient.PatientBasicInformationResponseVM;

import java.io.Serializable;
import java.time.Instant;

public record PatientAdministrativeWarningsResponseVM(
        Long id,
        PatientBasicInformationResponseVM patient,
        String warningType,
        String description,
        Boolean resolved,
        String resolvedBy,
        Instant resolvedDate,
        String undoResolvedBy,
        Instant undoResolvedDate,
        String createdBy,
        Instant createdDate

) implements Serializable {
    public static PatientAdministrativeWarningsResponseVM ofEntity(PatientAdministrativeWarnings entity) {
        return new PatientAdministrativeWarningsResponseVM(
                entity.getId(),
                PatientBasicInformationResponseVM.ofEntity(entity.getPatient()),
                entity.getWarningType(),
                entity.getDescription(),
                entity.getResolved(),
                entity.getResolvedBy(),
                entity.getResolvedDate(),
                entity.getUndoResolvedBy(),
                entity.getUndoResolvedDate(),
                entity.getCreatedBy(),
                entity.getCreatedDate()
        );
    }
}

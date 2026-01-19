package com.dazzle.asklepios.web.rest.vm.PatientProblems;

import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import java.time.Instant;
import java.util.Date;

public record PatientProblemResponseVM(
        Long id,
        Long patientId,
        String condition,
        Date dateOfDiagnosis,
        EncounterVaccinationStatus status,
        String type,
        Date dateOfResolution,
        Boolean byPatient,
        String sourceOfInformation,
        String createdBy,
        Instant createdDate
) {
    public static PatientProblemResponseVM ofEntity(PatientProblem entity) {
        return new PatientProblemResponseVM(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getCondition(),
                entity.getDateOfDiagnosis(),
                entity.getStatus(),
                entity.getType(),
                entity.getDateOfResolution(),
                entity.getByPatient(),
                entity.getSourceOfInformation(),
                entity.getCreatedBy(),
                entity.getCreatedDate()
        );
    }
}



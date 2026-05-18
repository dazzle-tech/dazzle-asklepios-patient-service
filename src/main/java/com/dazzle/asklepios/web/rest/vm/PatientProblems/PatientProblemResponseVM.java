package com.dazzle.asklepios.web.rest.vm.PatientProblems;

import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Date;

public record PatientProblemResponseVM(
        Long id,
        Long patientId,
        String condition,
        Date dateOfDiagnosis,


        @NotNull
        EncounterVaccinationStatus conditionStatus,

        String type,
        Date dateOfResolution,
        Boolean byPatient,
        String sourceOfInformation,

        PatientHistoryStatus status,

        String cancelledBy,
        Instant cancelledDate,
        String cancellationReason,

        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
) {
    public static PatientProblemResponseVM ofEntity(PatientProblem entity) {
        return new PatientProblemResponseVM(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getCondition(),
                entity.getDateOfDiagnosis(),

                entity.getConditionStatus(),

                entity.getType(),
                entity.getDateOfResolution(),
                entity.getByPatient(),
                entity.getSourceOfInformation(),

                entity.getStatus(),

                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason(),

                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}
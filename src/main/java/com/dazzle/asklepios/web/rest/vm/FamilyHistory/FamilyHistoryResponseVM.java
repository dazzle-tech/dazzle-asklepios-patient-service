package com.dazzle.asklepios.web.rest.vm.FamilyHistory;

import com.dazzle.asklepios.domain.FamilyHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.Relations;

import java.time.Instant;
import java.util.Date;

public record FamilyHistoryResponseVM(
        Long id,
        Long patientId,
        String condition,
        Relations relation,
        Boolean inheritedDiseases,

        PatientHistoryStatus status,
        String cancelledBy,
        Date cancelledDate,
        String cancellationReason,

        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
) {
    public static FamilyHistoryResponseVM ofEntity(FamilyHistory entity) {
        return new FamilyHistoryResponseVM(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getCondition(),
                entity.getRelation(),
                entity.getInheritedDiseases(),

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
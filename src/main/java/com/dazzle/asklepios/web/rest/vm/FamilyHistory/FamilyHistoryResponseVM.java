package com.dazzle.asklepios.web.rest.vm.FamilyHistory;

import com.dazzle.asklepios.domain.FamilyHistory;
import com.dazzle.asklepios.domain.enumeration.Relations;

import java.time.Instant;

public record FamilyHistoryResponseVM(
        Long id,
        Long patientId,
        String condition,
        Relations relation,
        String inheritedDiseases,
        String createdBy,
        Instant createdDate
) {
    public static FamilyHistoryResponseVM ofEntity(FamilyHistory entity) {
        return new FamilyHistoryResponseVM(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getCondition(),
                entity.getRelation(),
                entity.getInheritedDiseases(),
                entity.getCreatedBy(),
                entity.getCreatedDate()
        );
    }
}

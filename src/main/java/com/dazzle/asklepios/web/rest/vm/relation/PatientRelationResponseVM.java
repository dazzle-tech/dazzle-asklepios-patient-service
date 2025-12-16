package com.dazzle.asklepios.web.rest.vm.relation;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.web.rest.vm.patient.PatientResponseVM;

public record PatientRelationResponseVM(
        Long id,
        Long patientId,
        Long relativePatientId,
        String relationType,
        String categoryType,
        Boolean isActive,
        Patient patient ,
        Patient relativePatient
) {
    public static PatientRelationResponseVM fromEntity(PatientRelation e) {
        return new PatientRelationResponseVM(
                e.getId(),
                e.getPatient() != null ? e.getPatient().getId() : null,
                e.getRelativePatient() != null ? e.getRelativePatient().getId() : null,
                e.getRelationType() != null ? e.getRelationType().name() : null,
                e.getCategoryType() != null ? e.getCategoryType().name() : null,
                e.getIsActive(),
                 e.getPatient(),
                e.getRelativePatient()
        );
    }
}

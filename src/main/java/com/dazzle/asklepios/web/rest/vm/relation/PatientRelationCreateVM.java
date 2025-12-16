package com.dazzle.asklepios.web.rest.vm.relation;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.domain.enumeration.FamilyMemberCategory;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import jakarta.validation.constraints.NotNull;

public record PatientRelationCreateVM(

        @NotNull Long patientId,
        @NotNull Long relativePatientId,
        @NotNull RelationType relationType,
        FamilyMemberCategory categoryType
) {
    public PatientRelation toEntity() {
        return PatientRelation.builder()
                .patient(Patient.builder().id(patientId).build())
                .relativePatient(Patient.builder().id(relativePatientId).build())
                .relationType(relationType)
                .categoryType(categoryType)
                .isActive(true)
                .build();
    }
}

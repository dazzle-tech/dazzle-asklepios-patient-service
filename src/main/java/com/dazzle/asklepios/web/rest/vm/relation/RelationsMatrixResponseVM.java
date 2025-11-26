package com.dazzle.asklepios.web.rest.vm.relation;

import com.dazzle.asklepios.domain.RelationsMatrix;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.RelationType;

public record RelationsMatrixResponseVM(
        Long id,
        Gender firstPatientGender,
        Gender secondPatientGender,
        RelationType firstRelationCode,
        RelationType secondRelationCode
) {
    public static RelationsMatrixResponseVM fromEntity(RelationsMatrix e) {
        return new RelationsMatrixResponseVM(
                e.getId(),
                e.getFirstPatientGender(),
                e.getSecondPatientGender(),
                e.getFirstRelationCode(),
                e.getSecondRelationCode()
        );
    }
}

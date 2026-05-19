package com.dazzle.asklepios.web.rest.vm.patientMerge;

import java.util.List;

public record PatientMergeTransactionChangesVM(
        Long mergeLogId,
        List<FieldChangeVM> fieldChanges
) {
    public record FieldChangeVM(
            String entityName,
            String tableName,
            Long fromRecordId,
            Long toRecordId,
            String fieldName,
            String fieldLabel,
            String oldValue,
            String newValue,
            String decision,
            String fieldType,
            String inputType,
            String inputSource
    ) {
    }
}
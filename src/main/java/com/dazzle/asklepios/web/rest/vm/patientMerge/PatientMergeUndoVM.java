package com.dazzle.asklepios.web.rest.vm.patientMerge;

public record PatientMergeUndoVM(
        Long mergeLogId,
        Long fromPatientId,
        Long toPatientId,
        String status,
        Integer restoredFieldsCount,
        Integer restoredRecordsCount
) {
}
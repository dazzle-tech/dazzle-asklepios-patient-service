package com.dazzle.asklepios.web.rest.vm.patientMerge;

public record PatientMergeExecuteVM(
        Long mergeLogId,
        Long fromPatientId,
        Long toPatientId,
        String status
) {
}
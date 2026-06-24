package com.dazzle.asklepios.web.rest.vm.patientMerge;

import java.time.Instant;

public record PatientMergeTransactionVM(

        Long mergeLogId,
        String transactionNumber,

        Long fromPatientId,
        String fromPatientName,
        String fromPatientMrn,

        Long toPatientId,
        String toPatientName,
        String toPatientMrn,

        String mergeStatus,
        String mergedBy,
        Instant mergedAt,

        String undoBy,
        Instant undoAt,

        String reason,

        Boolean canUndo
) {
}
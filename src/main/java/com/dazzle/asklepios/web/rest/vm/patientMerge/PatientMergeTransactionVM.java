package com.dazzle.asklepios.web.rest.vm.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergedStatus;

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

        MergedStatus mergeStatus,
        String mergedBy,
        Instant mergedAt,

        String undoBy,
        Instant undoAt,

        String reason,

        Boolean canUndo
) {
}
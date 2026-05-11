package com.dazzle.asklepios.web.rest.vm.patientMerge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeTransactionVM {

    private Long mergeLogId;

    private Long fromPatientId;
    private String fromPatientName;

    private Long toPatientId;
    private String toPatientName;

    private String mergeStatus;
    private String mergedBy;
    private Instant mergedAt;

    private String undoneBy;
    private Instant undoneAt;

    private String reason;

    private Boolean canUndo;
}
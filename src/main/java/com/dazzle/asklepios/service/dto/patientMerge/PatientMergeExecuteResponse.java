package com.dazzle.asklepios.service.dto.patientMerge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeExecuteResponse {

    private Long mergeLogId;
    private Long fromPatientId;
    private Long toPatientId;
    private String status;
}
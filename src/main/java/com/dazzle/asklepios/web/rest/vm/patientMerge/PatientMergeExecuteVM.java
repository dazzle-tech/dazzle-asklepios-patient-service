package com.dazzle.asklepios.web.rest.vm.patientMerge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeExecuteVM {

    private Long mergeLogId;
    private Long fromPatientId;
    private Long toPatientId;
    private String status;
}
package com.dazzle.asklepios.service.dto.patientMerge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeExecuteDTO {

    private Long fromPatientId;
    private Long toPatientId;

    private String reason;

    private List<PatientMergeDecisionDTO> decisions;
}
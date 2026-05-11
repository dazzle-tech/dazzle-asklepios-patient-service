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
public class PatientMergePreviewResponse {

    private Long fromPatientId;
    private Long toPatientId;

    private List<PatientMergeConflictDTO> conflicts;

    private List<PatientMergeAutoTransferDTO> autoTransfers;
}
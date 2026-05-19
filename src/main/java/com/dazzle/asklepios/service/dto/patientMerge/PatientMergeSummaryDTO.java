package com.dazzle.asklepios.service.dto.patientMerge;

import java.util.List;

public record PatientMergeSummaryDTO(
        Long fromPatientId,
        Long toPatientId,
        String reason,
        List<PatientMergeDecisionDTO> decisions,
        List<PatientMergeAutoTransferDTO> autoTransfers
) {
}
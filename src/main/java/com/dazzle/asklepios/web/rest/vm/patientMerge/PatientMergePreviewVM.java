package com.dazzle.asklepios.web.rest.vm.patientMerge;

import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeAutoTransferDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeConflictDTO;

import java.util.List;

public record PatientMergePreviewVM(
        Long fromPatientId,
        Long toPatientId,
        List<PatientMergeConflictDTO> conflicts,
        List<PatientMergeAutoTransferDTO> autoTransfers
) {
}
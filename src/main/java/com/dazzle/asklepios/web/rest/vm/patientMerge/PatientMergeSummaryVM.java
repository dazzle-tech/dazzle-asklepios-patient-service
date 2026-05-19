package com.dazzle.asklepios.web.rest.vm.patientMerge;

import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryItemDTO;

import java.util.List;

public record PatientMergeSummaryVM(
        Long fromPatientId,
        Long toPatientId,
        List<PatientMergeSummaryItemDTO> fieldUpdates,
        List<PatientMergeSummaryItemDTO> recordsToAdd,
        List<PatientMergeSummaryItemDTO> ignoredItems,
        List<PatientMergeSummaryItemDTO> autoTransfers
) {
}
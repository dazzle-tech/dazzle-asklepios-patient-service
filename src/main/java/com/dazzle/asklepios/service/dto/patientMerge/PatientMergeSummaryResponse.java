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
public class PatientMergeSummaryResponse {

    private Long fromPatientId;
    private Long toPatientId;

    private List<PatientMergeSummaryItemDTO> fieldUpdates;
    private List<PatientMergeSummaryItemDTO> recordsToAdd;
    private List<PatientMergeSummaryItemDTO> ignoredItems;
    private List<PatientMergeSummaryItemDTO> autoTransfers;
}
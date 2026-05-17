package com.dazzle.asklepios.web.rest.vm.patientMerge;

import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryItemDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeSummaryVM {

    private Long fromPatientId;
    private Long toPatientId;

    private List<PatientMergeSummaryItemDTO> fieldUpdates;
    private List<PatientMergeSummaryItemDTO> recordsToAdd;
    private List<PatientMergeSummaryItemDTO> ignoredItems;
    private List<PatientMergeSummaryItemDTO> autoTransfers;
}
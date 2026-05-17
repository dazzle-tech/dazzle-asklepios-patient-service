package com.dazzle.asklepios.web.rest.vm.patientMerge;

import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeAutoTransferDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeConflictDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergePreviewVM {

    private Long fromPatientId;
    private Long toPatientId;

    private List<PatientMergeConflictDTO> conflicts;

    private List<PatientMergeAutoTransferDTO> autoTransfers;
}
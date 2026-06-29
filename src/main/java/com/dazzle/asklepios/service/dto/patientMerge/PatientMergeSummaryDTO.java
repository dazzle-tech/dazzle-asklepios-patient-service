package com.dazzle.asklepios.service.dto.patientMerge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PatientMergeSummaryDTO(

        @NotNull
        Long fromPatientId,

        @NotNull
        Long toPatientId,

        String reason,

        List<@Valid PatientMergeDecisionDTO> decisions,

        @NotNull
        List<@Valid PatientMergeAutoTransferDTO> autoTransfers

) {
}
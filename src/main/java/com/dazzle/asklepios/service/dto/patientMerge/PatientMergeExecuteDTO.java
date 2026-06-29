package com.dazzle.asklepios.service.dto.patientMerge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PatientMergeExecuteDTO(
        @NotNull
        Long fromPatientId,

        @NotNull
        Long toPatientId,

        @NotBlank
        String reason,

        @NotEmpty
        List<@Valid PatientMergeDecisionDTO> decisions
) {
}
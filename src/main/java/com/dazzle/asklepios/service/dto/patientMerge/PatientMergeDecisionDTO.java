package com.dazzle.asklepios.service.dto.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatientMergeDecisionDTO(
        @NotBlank
        String entityName,

        @NotBlank
        String tableName,

        @NotNull
        Long fromRecordId,

        Long toRecordId,

        String matchKey,

        @NotBlank
        String fieldName,

        @NotBlank
        String fieldLabel,

        String fromValue,

        String toValue,

        @NotNull
        MergeDecision suggestedDecision,

        @NotNull
        MergeDecision finalDecision,

        String selectedValue,

        String fieldType,

        String inputType,

        String inputSource
) {
}
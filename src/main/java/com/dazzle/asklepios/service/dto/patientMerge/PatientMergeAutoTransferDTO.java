package com.dazzle.asklepios.service.dto.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatientMergeAutoTransferDTO(

        @NotBlank
        String entityName,

        @NotBlank
        String tableName,

        @NotNull
        Long fromRecordId,

        Long toRecordId,

        @NotBlank
        String matchKey,

        String fieldName,

        @NotBlank
        String fieldLabel,

        @NotBlank
        String fromValue,

        String toValue,

        @NotBlank
        String selectedValue,

        @NotNull
        MergeDecision suggestedDecision,

        String fieldType,

        String inputType,

        String inputSource
) {
}
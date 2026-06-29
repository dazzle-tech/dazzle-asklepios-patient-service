package com.dazzle.asklepios.service.dto.patientMerge;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatientMergeSummaryItemDTO(

        @NotBlank
        String entityName,

        @NotBlank
        String tableName,

        @NotNull
        Long fromRecordId,

        Long toRecordId,

        String matchKey,

        String fieldName,

        @NotBlank
        String fieldLabel,

        String oldValue,

        String newValue,

        @NotNull
        MergeDecision decision,

        String fieldType,

        String inputType,

        String inputSource
) {
}
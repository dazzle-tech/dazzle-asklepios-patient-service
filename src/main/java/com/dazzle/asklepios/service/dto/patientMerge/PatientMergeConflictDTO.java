package com.dazzle.asklepios.service.dto.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import jakarta.validation.constraints.NotBlank;
import org.wildfly.common.annotation.NotNull;

public record PatientMergeConflictDTO(
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
        String fieldType,
        String inputType,
        String inputSource

) {
}
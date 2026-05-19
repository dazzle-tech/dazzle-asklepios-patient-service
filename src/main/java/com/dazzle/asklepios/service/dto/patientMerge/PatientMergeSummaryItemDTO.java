package com.dazzle.asklepios.service.dto.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;

public record PatientMergeSummaryItemDTO(
        String entityName,
        String tableName,
        Long fromRecordId,
        Long toRecordId,
        String matchKey,
        String fieldName,
        String fieldLabel,
        String oldValue,
        String newValue,
        MergeDecision decision,
        String fieldType,
        String inputType,
        String inputSource
) {
}
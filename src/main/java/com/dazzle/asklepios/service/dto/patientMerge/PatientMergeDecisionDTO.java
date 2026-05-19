package com.dazzle.asklepios.service.dto.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;

public record PatientMergeDecisionDTO(
        String entityName,
        String tableName,
        Long fromRecordId,
        Long toRecordId,
        String matchKey,
        String fieldName,
        String fieldLabel,
        String fromValue,
        String toValue,
        MergeDecision suggestedDecision,
        MergeDecision finalDecision,
        String selectedValue,
        String fieldType,
        String inputType,
        String inputSource
) {
}
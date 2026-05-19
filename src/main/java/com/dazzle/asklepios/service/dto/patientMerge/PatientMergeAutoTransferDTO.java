package com.dazzle.asklepios.service.dto.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;

public record PatientMergeAutoTransferDTO(
        String entityName,
        String tableName,
        Long fromRecordId,
        Long toRecordId,
        String matchKey,
        String fieldName,
        String fieldLabel,
        String fromValue,
        String toValue,
        String selectedValue,
        MergeDecision suggestedDecision,
        String fieldType,
        String inputType,
        String inputSource
) {
}
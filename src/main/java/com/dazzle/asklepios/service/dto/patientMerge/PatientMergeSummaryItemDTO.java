package com.dazzle.asklepios.service.dto.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeSummaryItemDTO extends PatientMergeFieldMetadataDTO{

    private String entityName;
    private String tableName;

    private Long fromRecordId;
    private Long toRecordId;

    private String matchKey;

    private String fieldName;
    private String fieldLabel;

    private String oldValue;
    private String newValue;
    private MergeDecision decision;
}
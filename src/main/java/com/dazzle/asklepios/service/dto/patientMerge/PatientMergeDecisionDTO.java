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
public class PatientMergeDecisionDTO extends PatientMergeFieldMetadataDTO {

    private String entityName;
    private String tableName;

    private Long fromRecordId;
    private Long toRecordId;

    private String matchKey;

    private String fieldName;
    private String fieldLabel;

    private String fromValue;
    private String toValue;

    private MergeDecision suggestedDecision;
    private MergeDecision finalDecision;
    private String selectedValue;
}
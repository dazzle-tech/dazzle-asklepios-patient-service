package com.dazzle.asklepios.web.rest.vm.patientMerge;

import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeFieldMetadataDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeTransactionChangesVM {

    private Long mergeLogId;

    private List<FieldChangeVM> fieldChanges;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldChangeVM extends PatientMergeFieldMetadataDTO {
        private String entityName;
        private String tableName;
        private Long fromRecordId;
        private Long toRecordId;
        private String fieldName;
        private String fieldLabel;
        private String oldValue;
        private String newValue;
        private String decision;
    }

}
package com.dazzle.asklepios.service.dto.patientMerge;

import java.util.List;

public record PatientMergeTableConfigSaveDTO(
        Long id,
        String entityName,
        String tableName,
        String primaryKeyColumnName,
        String patientColumnName,
        Boolean enabled,
        Integer sortOrder,
        String mergeCategory,
        Boolean autoDiscoverFields,
        List<String> matchKeyColumns,
        List<String> excludedColumns
) {
}
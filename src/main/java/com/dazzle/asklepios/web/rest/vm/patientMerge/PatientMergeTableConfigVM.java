package com.dazzle.asklepios.web.rest.vm.patientMerge;

import java.util.List;

public record PatientMergeTableConfigVM(

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

        List<String> excludedColumns,

        List<String> availableColumns
) {
}
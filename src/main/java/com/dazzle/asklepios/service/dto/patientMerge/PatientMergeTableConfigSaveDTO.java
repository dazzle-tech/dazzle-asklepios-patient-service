package com.dazzle.asklepios.service.dto.patientMerge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PatientMergeTableConfigSaveDTO(

        Long id,

        @NotBlank
        String entityName,

        @NotBlank
        String tableName,

        @NotBlank
        String primaryKeyColumnName,

        @NotBlank
        String patientColumnName,

        @NotNull
        Boolean enabled,

        Integer sortOrder,

        @NotBlank
        String mergeCategory,

        Boolean autoDiscoverFields,

        List<String> matchKeyColumns,

        List<String> excludedColumns
) {
}
package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SupportiveDocumentRequirementCatalogDTO(
        String code,
        String label,
        String medicalSheetCode,
        String medicalSheetName,
        String medicalSheetPath,
        Integer displayOrder,
        List<SupportiveDocumentMedicalSheetRefDTO> additionalMedicalSheets
) {
}

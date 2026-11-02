package com.dazzle.asklepios.service.dto.patientEncounter;

import java.io.Serializable;

public record MissingSupportiveDocumentRequirementDTO(
        String code,
        String label,
        String medicalSheetCode,
        String medicalSheetName,
        String medicalSheetPath
) implements Serializable {
}

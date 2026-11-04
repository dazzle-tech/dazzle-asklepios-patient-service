package com.dazzle.asklepios.service.dto.externalresult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** One uploaded radiology document: the reviewed reports plus what the AI extracted from the file. */
public record ExternalRadiologyReportSaveDTO(
        @NotNull Long patientId,
        Long encounterId,
        String rawExtractedText,
        @Size(max = 10) String detectedLanguage,
        Boolean translated,
        @NotEmpty @Valid List<ExternalRadiologyReportItemDTO> reports
) {
}

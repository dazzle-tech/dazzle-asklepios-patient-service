package com.dazzle.asklepios.service.dto.externalresult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** One uploaded lab document: the reviewed rows plus what the AI extracted from the file. */
public record ExternalLabResultSaveDTO(
        @NotNull Long patientId,
        Long encounterId,
        String rawExtractedText,
        @Size(max = 10) String detectedLanguage,
        Boolean translated,
        @NotEmpty @Valid List<ExternalLabResultItemDTO> results
) {
}

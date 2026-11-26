package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.DiagnosisType;
import java.time.Instant;

public record ClaimEncounterDiagnosisResponse(
        Long id,
        Long claimEncounterCopyId,
        Long encounterId,
        Long diagnosisId,
        DiagnosisType type,
        Boolean suspected,
        Boolean major,
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate,
        String originalCreatedBy,
        Instant originalCreatedDate
) {}
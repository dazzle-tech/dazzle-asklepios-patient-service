package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.DiagnosisType;

public record ClaimEncounterDiagnosisResponse(
        Long id,
        Long claimEncounterCopyId,
        Long encounterId,
        Long diagnosisId,
        DiagnosisType type,
        Boolean suspected,
        Boolean major
) {}
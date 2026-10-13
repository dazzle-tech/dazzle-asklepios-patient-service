package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.DiagnosisType;

public record ClaimEncounterDiagnosisRequest(
        Long id,
        Long diagnosisId,
        DiagnosisType type,
        Boolean suspected,
        Boolean major
) {}
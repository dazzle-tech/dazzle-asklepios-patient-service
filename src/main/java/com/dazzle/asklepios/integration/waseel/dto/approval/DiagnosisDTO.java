package com.dazzle.asklepios.integration.waseel.dto.approval;

public record DiagnosisDTO(
        Integer sequence,
        String diagnosisDescription,
        String type,
        String diagnosisCode
) {}
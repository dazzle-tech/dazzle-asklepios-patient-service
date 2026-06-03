package com.dazzle.asklepios.integration.waseel.dto.approval;

public record WaseelApprovalDiagnosis(
        Integer sequence,
        String diagnosisDescription,
        String type,
        String diagnosisCode
) {}
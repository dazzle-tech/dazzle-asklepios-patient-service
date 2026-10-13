package com.dazzle.asklepios.service.dto.billing;


import java.math.BigDecimal;

public record ClaimEncounterCopyResponse(
        Long id,
        Long encounterId,
        String chiefComplaint,
        String historyOfPresentIllness,
        String physicalExamination,
        String assessment,
        String treatmentPlan,
        Integer pulse,
        BigDecimal temperature,
        Integer respiratoryRate,
        BigDecimal oxygenSaturation,
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        BigDecimal height,
        BigDecimal weight
) {
}
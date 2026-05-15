package com.dazzle.asklepios.integration.waseel.dto.eligibility;

public record EligibilityTestRequest(
        Long patientId,
        Long visitId,
        Long patientInsuranceId,
        String serviceDate,
        Boolean isEmergency,
        Boolean referral
) {}
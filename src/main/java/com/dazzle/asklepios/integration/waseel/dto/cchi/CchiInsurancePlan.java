package com.dazzle.asklepios.integration.waseel.dto.cchi;

public record CchiInsurancePlan(
        String policyNumber,
        String memberCardId,
        String payerId,
        String payerName,
        String payerNphiesId,
        String coverageType,
        String expiryDate,
        String relationWithSubscriber,
        String patientShare,
        String maxLimit,
        String networkId,
        String policyClassName,
        Boolean isPrimary
) {}
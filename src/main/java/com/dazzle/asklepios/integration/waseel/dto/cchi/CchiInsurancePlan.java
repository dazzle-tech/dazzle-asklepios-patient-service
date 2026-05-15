package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CchiInsurancePlan(
        String memberCardId,
        String policyNumber,
        String expiryDate,
        String isPrimary,
        String payerId,
        String relationWithSubscriber,
        String coverageType,
        Integer patientShare,
        Integer maxLimit,
        String networkId,
        String policyClassName,
        String policyHolder,
        String payerNphiesId,
        Boolean newPlan
) {}
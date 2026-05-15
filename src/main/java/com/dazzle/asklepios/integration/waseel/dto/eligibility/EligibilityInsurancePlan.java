package com.dazzle.asklepios.integration.waseel.dto.eligibility;

import java.util.List;

public record EligibilityInsurancePlan(
        String planId,
        String payerId,
        String payerName,
        String memberCardId,
        String policyNumber,
        String payerNphiesId,
        String tpaNphiesId,
        String expiryDate,
        String relationWithSubscriber,
        String coverageType,
        Object patientShare,
        Object maxLimit,
        List<EligibilityCoverageClass> coverageClassList,
        String policyHolder,
        Boolean primary
) {}
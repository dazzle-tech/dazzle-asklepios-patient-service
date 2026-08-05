package com.dazzle.asklepios.integration.waseel.dto.eligibility;

import java.util.List;

public record EligibilityInsurancePlanDTO(

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
        Integer patientShare,
        Integer maxLimit,
        List<CoverageClassDTO> coverageClassList,
        String policyHolder,
        Boolean primary

) {}
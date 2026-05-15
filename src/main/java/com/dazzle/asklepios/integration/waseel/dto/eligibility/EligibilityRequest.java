package com.dazzle.asklepios.integration.waseel.dto.eligibility;

import java.util.Map;

public record EligibilityRequest(
        Boolean isNewBorn,
        EligibilityBeneficiary beneficiary,
        Object subscriber,
        EligibilityInsurancePlan insurancePlan,
        String serviceDate,
        String toDate,
        Boolean benefits,
        Boolean discovery,
        Boolean validation,
        Boolean transfer,
        Boolean isEmergency,
        Boolean referral,
        Map<String, Object> referredClinic,
        String destinationId
) {}
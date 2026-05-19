package com.dazzle.asklepios.integration.waseel.dto.eligibility;

public record EligibilityRequest(

        Boolean isNewBorn,

        EligibilityBeneficiaryDTO beneficiary,

        Object subscriber,

        EligibilityInsurancePlanDTO insurancePlan,

        String serviceDate,

        String toDate,

        Boolean benefits,

        Boolean discovery,

        Boolean validation,

        Boolean transfer,

        Boolean isEmergency,

        Boolean referral,

        Object referredClinic,

        String destinationId

) {}
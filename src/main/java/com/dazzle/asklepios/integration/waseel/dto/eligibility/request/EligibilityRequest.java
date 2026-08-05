package com.dazzle.asklepios.integration.waseel.dto.eligibility.request;

import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityBeneficiaryDTO;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityInsurancePlanDTO;

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
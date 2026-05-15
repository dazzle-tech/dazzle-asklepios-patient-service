package com.dazzle.asklepios.integration.waseel.dto.eligibility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibilityCoverageDTO(
        String memberId,
        String policyNumber,
        String policyHolder,
        String inforce,
        String notInforceReason,
        String benefitStartDate,
        String benefitEndDate,
        String type,
        String relationship,
        String subscriberMemberId,
        String status,
        String siteEligibility,
        Object items,
        String network,
        String subrogation,
        List<EligibilityClassDTO> classList,
        List<EligibilityCostBeneficiaryDTO> costBeneficiaries
) {}
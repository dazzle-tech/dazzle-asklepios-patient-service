package com.dazzle.asklepios.integration.waseel.dto;

import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Parsed Waseel eligibility coverage used for insurance billing display.
 */
public record WaseelCoverageDetails(

        Long eligibilityRequestId,

        String eligibilityResponseId,

        Long patientInsuranceId,

        String memberId,

        String policyNumber,

        String policyHolder,

        String network,

        String inforce,

        String coverageStatus,

        BigDecimal copaymentPercent,

        BigDecimal copaymentCap,

        Instant eligibilityCheckedAt,

        List<WaseelBenefitDetail> benefits,

        List<InsuranceBenefitRule> benefitRules,

        String policyClassName,

        LocalDate expiryDate,

        String payerName,

        String coverageType,

        String relationWithSubscriber,

        String planCode,

        String groupName,

        String groupNumber

) implements Serializable {
}

package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.WaseelCoverageDetails;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.service.InsuranceBenefitRuleMatcher;
import com.dazzle.asklepios.service.InsuranceBenefitRuleService;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaseelCoverageQueryService {

    private static final String ENTITY_NAME =
            "waseelCoverage";

    private static final String SUCCESS_STATUS =
            "SUCCESS";

    private final WaseelEligibilityRequestRepository
            eligibilityRequestRepository;

    private final WaseelCoverageExtractionService
            coverageExtractionService;

    private final InsuranceBenefitRuleService insuranceBenefitRuleService;

    private final InsuranceBenefitRuleMatcher benefitRuleMatcher;

    public WaseelCoverageDetails getLatestForPatient(
            Long patientId,
            Long patientInsuranceId
    ) {
        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required.",
                    ENTITY_NAME,
                    "patient.required"
            );
        }

        WaseelEligibilityRequest eligibility =
                resolveEligibilityRequest(
                        patientId,
                        patientInsuranceId
                );

        if (
                eligibility.getResponseJson() == null
                        || eligibility.getResponseJson().isBlank()
        ) {
            throw new BadRequestAlertException(
                    "Waseel eligibility response JSON is missing.",
                    ENTITY_NAME,
                    "eligibility.response.missing"
            );
        }

        WaseelCoverageDetails details =
                coverageExtractionService
                        .extractCoverageDetails(
                                eligibility.getResponseJson()
                        );

        java.util.List<InsuranceBenefitRule> storedRules =
                eligibility.getPatientInsuranceId() == null
                        ? java.util.List.of()
                        : insuranceBenefitRuleService.getStoredRules(
                                eligibility.getPatientInsuranceId()
                        );

        java.util.List<InsuranceBenefitRule> benefitRules =
                storedRules.isEmpty()
                        ? details.benefitRules()
                        : storedRules;

        InsuranceBenefitRule preferredRule =
                benefitRuleMatcher.selectPreferredDefaultRule(
                        benefitRules,
                        true
                );

        java.math.BigDecimal copaymentPercent = details.copaymentPercent();
        java.math.BigDecimal copaymentCap = details.copaymentCap();

        if (preferredRule != null) {
            if (preferredRule.patientCopaymentPercentage() != null) {
                copaymentPercent = preferredRule.patientCopaymentPercentage();
            }
            if (preferredRule.patientMaximumCopayment() != null) {
                copaymentCap = preferredRule.patientMaximumCopayment();
            }
        }

        return new WaseelCoverageDetails(
                eligibility.getId(),
                eligibility.getEligibilityResponseId(),
                eligibility.getPatientInsuranceId(),
                details.memberId(),
                details.policyNumber(),
                details.policyHolder(),
                details.network(),
                details.inforce(),
                details.coverageStatus(),
                copaymentPercent,
                copaymentCap,
                eligibility.getRespondedAt(),
                details.benefits(),
                benefitRules
        );
    }

    private WaseelEligibilityRequest resolveEligibilityRequest(
            Long patientId,
            Long patientInsuranceId
    ) {
        if (patientInsuranceId != null) {
            return eligibilityRequestRepository
                    .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                            patientId,
                            patientInsuranceId,
                            SUCCESS_STATUS
                    )
                    .or(() ->
                            eligibilityRequestRepository
                                    .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                                            patientId,
                                            SUCCESS_STATUS
                                    )
                    )
                    .orElseThrow(() ->
                            new NotFoundAlertException(
                                    "No successful Waseel eligibility response was found for this patient.",
                                    ENTITY_NAME,
                                    "eligibility.notfound"
                            )
                    );
        }

        return eligibilityRequestRepository
                .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        patientId,
                        SUCCESS_STATUS
                )
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "No successful Waseel eligibility response was found for this patient.",
                                ENTITY_NAME,
                                "eligibility.notfound"
                        )
                );
    }
}

package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.WaseelCoverageDetails;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.service.InsuranceBenefitRuleMatcher;
import com.dazzle.asklepios.service.InsuranceBenefitRuleService;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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

    private final PatientInsuranceRepository patientInsuranceRepository;

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

        PatientInsurance patientInsurance =
                resolvePatientInsurance(
                        patientId,
                        eligibility.getPatientInsuranceId(),
                        patientInsuranceId
                );

        String memberCardId = patientInsurance == null
                ? null
                : patientInsurance.getMemberCardId();
        String policyNumber = patientInsurance == null
                ? null
                : patientInsurance.getPolicyNumber();

        WaseelCoverageDetails details =
                coverageExtractionService
                        .extractCoverageDetails(
                                eligibility.getResponseJson(),
                                memberCardId,
                                policyNumber
                        );

        List<InsuranceBenefitRule> storedRules =
                eligibility.getPatientInsuranceId() == null
                        ? List.of()
                        : insuranceBenefitRuleService.getStoredRules(
                                eligibility.getPatientInsuranceId()
                        );

        List<InsuranceBenefitRule> benefitRules =
                storedRules.isEmpty()
                        ? details.benefitRules()
                        : storedRules;

        InsuranceBenefitRule preferredRule =
                benefitRuleMatcher.selectPreferredDefaultRule(
                        benefitRules,
                        true
                );

        BigDecimal copaymentPercent = details.copaymentPercent();
        BigDecimal copaymentCap = details.copaymentCap();

        if (preferredRule != null) {
            if (preferredRule.patientCopaymentPercentage() != null) {
                copaymentPercent = preferredRule.patientCopaymentPercentage();
            }
            if (preferredRule.patientMaximumCopayment() != null) {
                copaymentCap = preferredRule.patientMaximumCopayment();
            }
        }

        if (patientInsurance != null) {
            if (patientInsurance.getPatientShare() != null) {
                copaymentPercent = patientInsurance.getPatientShare();
            }
            if (patientInsurance.getMaxLimit() != null) {
                copaymentCap = patientInsurance.getMaxLimit();
            }
        }

        String memberId = firstNonBlank(
                patientInsurance == null ? null : patientInsurance.getMemberCardId(),
                details.memberId()
        );
        String resolvedPolicyNumber = firstNonBlank(
                patientInsurance == null ? null : patientInsurance.getPolicyNumber(),
                details.policyNumber()
        );
        String policyHolder = firstNonBlank(
                patientInsurance == null ? null : patientInsurance.getPolicyHolderName(),
                details.policyHolder()
        );
        String network = firstNonBlank(
                patientInsurance == null ? null : patientInsurance.getNetworkId(),
                details.network()
        );

        return new WaseelCoverageDetails(
                eligibility.getId(),
                eligibility.getEligibilityResponseId(),
                eligibility.getPatientInsuranceId(),
                memberId,
                resolvedPolicyNumber,
                policyHolder,
                network,
                details.inforce(),
                details.coverageStatus(),
                copaymentPercent,
                copaymentCap,
                eligibility.getRespondedAt(),
                details.benefits(),
                benefitRules
        );
    }

    private PatientInsurance resolvePatientInsurance(
            Long patientId,
            Long eligibilityPatientInsuranceId,
            Long requestedPatientInsuranceId
    ) {
        Long insuranceId = requestedPatientInsuranceId != null
                ? requestedPatientInsuranceId
                : eligibilityPatientInsuranceId;

        if (insuranceId == null) {
            return null;
        }

        return patientInsuranceRepository
                .findByIdAndPatient_Id(insuranceId, patientId)
                .orElse(null);
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

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}

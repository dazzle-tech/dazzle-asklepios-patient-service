package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.domain.PatientInsurance;
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

    private final PatientInsuranceRepository patientInsuranceRepository;

    private final EligibilityRequestPlanIdentityReader requestPlanIdentityReader;

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

        PatientInsurance insurance =
                resolvePatientInsurance(
                        patientId,
                        patientInsuranceId,
                        eligibility.getPatientInsuranceId()
                );

        WaseelCoverageDetails details =
                coverageExtractionService
                        .extractCoverageDetails(
                                eligibility.getResponseJson(),
                                insurance == null ? null : insurance.getMemberCardId(),
                                insurance == null ? null : insurance.getPolicyNumber()
                        );

        java.util.List<InsuranceBenefitRule> extractedRules =
                details.benefitRules() == null
                        ? java.util.List.of()
                        : details.benefitRules();

        java.util.List<InsuranceBenefitRule> storedRules =
                eligibility.getPatientInsuranceId() == null
                        ? java.util.List.of()
                        : insuranceBenefitRuleService.getStoredRules(
                                eligibility.getPatientInsuranceId()
                        );

        java.util.List<InsuranceBenefitRule> benefitRules =
                extractedRules.isEmpty()
                        ? storedRules
                        : extractedRules;

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

        return overlayPlanIdentity(
                new WaseelCoverageDetails(
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
                        benefitRules,
                        details.policyClassName(),
                        details.expiryDate(),
                        details.payerName(),
                        details.coverageType(),
                        details.relationWithSubscriber(),
                        details.planCode(),
                        details.groupName(),
                        details.groupNumber()
                ),
                insurance,
                eligibility
        );
    }

    private PatientInsurance resolvePatientInsurance(
            Long patientId,
            Long requestedInsuranceId,
            Long eligibilityInsuranceId
    ) {
        Long insuranceId = requestedInsuranceId != null
                ? requestedInsuranceId
                : eligibilityInsuranceId;

        if (insuranceId != null) {
            return patientInsuranceRepository.findById(insuranceId).orElse(null);
        }

        if (patientId == null) {
            return null;
        }

        return patientInsuranceRepository
                .findFirstByPatient_IdAndIsPrimaryTrue(patientId)
                .or(() -> patientInsuranceRepository.findFirstByPatient_Id(patientId))
                .orElse(null);
    }

    private WaseelCoverageDetails overlayPlanIdentity(
            WaseelCoverageDetails details,
            PatientInsurance insurance,
            WaseelEligibilityRequest eligibility
    ) {
        if (details == null) {
            return null;
        }

        EligibilityRequestPlanIdentityReader.PlanIdentity requestPlan =
                requestPlanIdentityReader.read(
                        eligibility == null ? null : eligibility.getRequestJson(),
                        insurance == null ? null : insurance.getMemberCardId(),
                        insurance == null ? null : insurance.getPolicyNumber()
                );

        return new WaseelCoverageDetails(
                details.eligibilityRequestId(),
                details.eligibilityResponseId(),
                insurance == null ? details.patientInsuranceId() : insurance.getId(),
                firstNonBlank(
                        requestPlan.memberCardId(),
                        insurance == null ? null : insurance.getMemberCardId(),
                        details.memberId()
                ),
                firstNonBlank(
                        requestPlan.policyNumber(),
                        insurance == null ? null : insurance.getPolicyNumber(),
                        details.policyNumber()
                ),
                firstNonBlank(
                        requestPlan.policyHolder(),
                        insurance == null ? null : insurance.getPolicyHolderName(),
                        details.policyHolder()
                ),
                firstNonBlank(details.network(), insurance == null ? null : insurance.getNetworkId()),
                firstNonBlank(details.inforce(), insurance == null ? null : insurance.getInforce()),
                firstNonBlank(
                        details.coverageStatus(),
                        insurance == null ? null : insurance.getEligibilityStatus()
                ),
                details.copaymentPercent() != null
                        ? details.copaymentPercent()
                        : (insurance == null ? null : insurance.getDefaultCopaymentPercent()),
                details.copaymentCap() != null && details.copaymentCap().signum() > 0
                        ? details.copaymentCap()
                        : firstDecimal(
                                insurance == null ? null : insurance.getMaxLimit(),
                                insurance == null ? null : insurance.getDefaultMaximumCopayment()
                        ),
                details.eligibilityCheckedAt(),
                details.benefits(),
                details.benefitRules(),
                firstNonBlank(
                        requestPlan.policyClassName(),
                        insurance == null ? null : insurance.getPolicyClassName(),
                        details.policyClassName()
                ),
                firstDate(
                        requestPlan.expiryDate(),
                        insurance == null ? null : insurance.getExpirationDate(),
                        details.expiryDate()
                ),
                firstNonBlank(
                        requestPlan.payerName(),
                        insurance == null ? null : insurance.getPayerName(),
                        details.payerName()
                ),
                firstNonBlank(
                        requestPlan.coverageType(),
                        insurance == null ? null : insurance.getCoverageType(),
                        details.coverageType()
                ),
                firstNonBlank(
                        requestPlan.relationWithSubscriber(),
                        insurance == null ? null : insurance.getRelationWithSubscriber(),
                        details.relationWithSubscriber()
                ),
                firstNonBlank(details.planCode(), insurance == null ? null : insurance.getPlanCode()),
                firstNonBlank(details.groupName(), insurance == null ? null : insurance.getGroupName()),
                firstNonBlank(details.groupNumber(), insurance == null ? null : insurance.getGroupNumber())
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private java.time.LocalDate firstDate(java.time.LocalDate... values) {
        if (values == null) {
            return null;
        }
        for (java.time.LocalDate value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private java.math.BigDecimal firstDecimal(
            java.math.BigDecimal first,
            java.math.BigDecimal second
    ) {
        if (first != null) {
            return first;
        }
        return second;
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

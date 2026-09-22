package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientInsuranceBenefitRule;
import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.repository.PatientInsuranceBenefitRuleRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InsuranceBenefitRuleService {

    private static final Logger LOG =
            LoggerFactory.getLogger(InsuranceBenefitRuleService.class);

    private static final String SUCCESS_STATUS = "SUCCESS";

    private final PatientInsuranceBenefitRuleRepository benefitRuleRepository;
    private final WaseelCoverageExtractionService coverageExtractionService;
    private final WaseelEligibilityRequestRepository waseelEligibilityRequestRepository;
    private final InsuranceBenefitRuleMatcher benefitRuleMatcher;
    private final ObjectMapper objectMapper;
    private final PayorClient payorClient;

    @Transactional
    public void syncFromCoverage(
            PatientInsurance insurance,
            EligibilityCoverageDTO coverage,
            Long eligibilityRequestId
    ) {
        if (insurance == null || insurance.getId() == null || coverage == null) {
            return;
        }

        List<InsuranceBenefitRule> extractedRules =
                coverageExtractionService.extractBenefitRules(coverage)
                        .stream()
                        .map(InsuranceBenefitRuleNormalizer::normalize)
                        .toList();

        benefitRuleRepository.deleteByPatientInsuranceId(insurance.getId());

        List<PatientInsuranceBenefitRule> entities = new ArrayList<>();
        for (InsuranceBenefitRule rule : extractedRules) {
            entities.add(toEntity(rule, insurance.getId(), eligibilityRequestId));
        }

        if (!entities.isEmpty()) {
            benefitRuleRepository.saveAll(entities);
        }

        InsuranceBenefitRule globalRule =
                extractedRules.stream()
                        .filter(InsuranceBenefitRule::globalDefault)
                        .findFirst()
                        .orElse(null);

        if (globalRule != null) {
            insurance.setDefaultCopaymentPercent(
                    globalRule.patientCopaymentPercentage()
            );
            insurance.setDefaultMaximumCopayment(
                    globalRule.patientMaximumCopayment()
            );
        }

        LOG.info(
                "[ELIGIBILITY] Synced {} benefit rules patientInsuranceId={} defaultCopayPercent={} defaultMaxCopay={}",
                entities.size(),
                insurance.getId(),
                insurance.getDefaultCopaymentPercent(),
                insurance.getDefaultMaximumCopayment()
        );
    }

    public List<InsuranceBenefitRule> getStoredRules(Long patientInsuranceId) {
        if (patientInsuranceId == null) {
            return List.of();
        }

        return benefitRuleRepository
                .findByPatientInsuranceIdOrderByBenefitCategoryAsc(patientInsuranceId)
                .stream()
                .map(this::toDto)
                .map(InsuranceBenefitRuleNormalizer::normalize)
                .toList();
    }

    public InsuranceBenefitRule resolveApplicableRule(
            PatientInsurance insurance,
            String serviceCategory,
            ServiceSource serviceSource
    ) {
        if (!isLatestCoverageInForce(insurance)) {
            return null;
        }

        List<InsuranceBenefitRule> extractedRules =
                insurance == null
                        ? List.of()
                        : loadRulesFromLatestEligibility(insurance);

        List<InsuranceBenefitRule> storedRules = List.of();
        if (insurance != null && insurance.getId() != null) {
            storedRules = getStoredRules(insurance.getId());
        }

        List<InsuranceBenefitRule> rules =
                extractedRules.isEmpty() ? storedRules : extractedRules;

        InsuranceBenefitRule matched =
                benefitRuleMatcher.resolveRule(
                        rules,
                        insurance,
                        serviceCategory,
                        serviceSource
                );

        if (matched != null) {
            return InsuranceBenefitRuleNormalizer.normalize(matched);
        }

        return buildFallbackFromInsuranceDefaults(insurance);
    }

    public boolean isLatestCoverageInForce(PatientInsurance insurance) {
        if (insurance == null) {
            return false;
        }

        if (!isWaseelCoverage(insurance)) {
            boolean inForce = isPatientInsuranceExpirationInForce(insurance);
            if (!inForce) {
                LOG.info(
                        "[INSURANCE] Non-Waseel coverage is not in-force "
                                + "patientInsuranceId={} expirationDate={}",
                        insurance.getId(),
                        insurance.getExpirationDate()
                );
            }
            return inForce;
        }

        WaseelEligibilityRequest eligibility = resolveEligibilityRequest(insurance);
        if (eligibility == null
                || eligibility.getResponseJson() == null
                || eligibility.getResponseJson().isBlank()) {
            return false;
        }

        boolean inForce = coverageExtractionService.isCoverageInForce(
                eligibility.getResponseJson()
        );

        if (!inForce) {
            LOG.info(
                    "[INSURANCE] Latest eligibility is not in-force "
                            + "patientInsuranceId={} eligibilityId={}",
                    insurance.getId(),
                    eligibility.getId()
            );
        }

        return inForce;
    }

    /**
     * Approval Coverage Co. and other non-Waseel payors have no Waseel
     * eligibility response. Coverage is in-force while the selected
     * patient insurance expiration date is today or in the future.
     */
    private boolean isPatientInsuranceExpirationInForce(PatientInsurance insurance) {
        return insurance.getExpirationDate() != null
                && !insurance.getExpirationDate().isBefore(LocalDate.now());
    }

    private boolean isWaseelCoverage(PatientInsurance insurance) {
        if (insurance.getPayorId() == null) {
            return false;
        }

        try {
            PayorDTO payor = payorClient.getPayorById(insurance.getPayorId());
            return payor != null && Boolean.TRUE.equals(payor.isWaseelEnabled());
        } catch (FeignException exception) {
            LOG.warn(
                    "[INSURANCE] Unable to resolve payor waseel flag payorId={}. "
                            + "Keeping Waseel eligibility in-force check.",
                    insurance.getPayorId(),
                    exception
            );
            return true;
        } catch (RuntimeException exception) {
            LOG.warn(
                    "[INSURANCE] Payor lookup failed payorId={}. "
                            + "Keeping Waseel eligibility in-force check.",
                    insurance.getPayorId(),
                    exception
            );
            return true;
        }
    }

    public void clearBenefitRules(Long patientInsuranceId) {
        if (patientInsuranceId == null) {
            return;
        }

        benefitRuleRepository.deleteByPatientInsuranceId(patientInsuranceId);
    }

    private List<InsuranceBenefitRule> loadRulesFromLatestEligibility(
            PatientInsurance insurance
    ) {
        WaseelEligibilityRequest eligibility =
                resolveEligibilityRequest(insurance);

        if (eligibility == null
                || eligibility.getResponseJson() == null
                || eligibility.getResponseJson().isBlank()) {
            return List.of();
        }

        return coverageExtractionService.extractBenefitRules(
                eligibility.getResponseJson(),
                insurance.getMemberCardId(),
                insurance.getPolicyNumber()
        );
    }

    private WaseelEligibilityRequest resolveEligibilityRequest(
            PatientInsurance insurance
    ) {
        Long patientId = insurance.getPatientId();
        if (patientId == null) {
            return null;
        }

        if (insurance.getId() != null) {
            return waseelEligibilityRequestRepository
                    .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                            patientId,
                            insurance.getId(),
                            SUCCESS_STATUS
                    )
                    .or(() ->
                            waseelEligibilityRequestRepository
                                    .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                                            patientId,
                                            SUCCESS_STATUS
                                    )
                    )
                    .orElse(null);
        }

        return waseelEligibilityRequestRepository
                .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        patientId,
                        SUCCESS_STATUS
                )
                .orElse(null);
    }

    private InsuranceBenefitRule buildFallbackFromInsuranceDefaults(
            PatientInsurance insurance
    ) {
        if (insurance == null) {
            return null;
        }

        if (insurance.getDefaultCopaymentPercent() == null
                && insurance.getDefaultMaximumCopayment() == null
                && insurance.getPatientShare() == null) {
            return null;
        }

        return new InsuranceBenefitRule(
                null,
                InsuranceBenefitRule.GLOBAL_CATEGORY,
                null,
                null,
                insurance.getNetworkId(),
                null,
                null,
                null,
                null,
                null,
                null,
                firstNonNull(
                        insurance.getDefaultCopaymentPercent(),
                        insurance.getPatientShare()
                ),
                insurance.getDefaultMaximumCopayment(),
                true,
                null
        );
    }

    private PatientInsuranceBenefitRule toEntity(
            InsuranceBenefitRule rule,
            Long patientInsuranceId,
            Long eligibilityRequestId
    ) {
        return PatientInsuranceBenefitRule.builder()
                .patientInsuranceId(patientInsuranceId)
                .eligibilityRequestId(eligibilityRequestId)
                .benefitCategory(rule.benefitCategory())
                .itemName(rule.itemName())
                .itemCode(rule.itemCode())
                .networkType(rule.networkType())
                .providerType(rule.providerType())
                .term(rule.term())
                .unit(rule.unit())
                .currency(rule.currency())
                .maximumBenefit(rule.maximumBenefit())
                .approvalLimit(rule.approvalLimit())
                .patientCopaymentPercentage(rule.patientCopaymentPercentage())
                .patientMaximumCopayment(rule.patientMaximumCopayment())
                .globalDefault(rule.globalDefault())
                .exceptionsJson(rule.exceptionsJson())
                .build();
    }

    private InsuranceBenefitRule toDto(PatientInsuranceBenefitRule entity) {
        return new InsuranceBenefitRule(
                entity.getId(),
                entity.getBenefitCategory(),
                entity.getItemName(),
                entity.getItemCode(),
                entity.getNetworkType(),
                entity.getProviderType(),
                entity.getTerm(),
                entity.getUnit(),
                entity.getCurrency(),
                entity.getMaximumBenefit(),
                entity.getApprovalLimit(),
                entity.getPatientCopaymentPercentage(),
                entity.getPatientMaximumCopayment(),
                Boolean.TRUE.equals(entity.getGlobalDefault()),
                entity.getExceptionsJson()
        );
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    String buildExceptionsJson(EligibilityCoverageDTO coverage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("costBeneficiaries", coverage.costBeneficiaries());
        payload.put("classList", coverage.classList());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}

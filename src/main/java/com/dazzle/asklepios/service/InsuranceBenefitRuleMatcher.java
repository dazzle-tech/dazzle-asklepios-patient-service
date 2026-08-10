package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class InsuranceBenefitRuleMatcher {

    public InsuranceBenefitRule resolveRule(
            List<InsuranceBenefitRule> rules,
            PatientInsurance insurance,
            String serviceCategory,
            ServiceSource serviceSource
    ) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        List<InsuranceBenefitRule> normalizedRules =
                rules.stream()
                        .map(InsuranceBenefitRuleNormalizer::normalize)
                        .toList();

        boolean preferInNetwork = preferInNetwork(insurance);

        InsuranceBenefitRule outpatientRule =
                findBestOutpatientRule(normalizedRules, preferInNetwork);
        if (outpatientRule != null && hasCopaymentPercent(outpatientRule)) {
            return outpatientRule;
        }

        if (isAmbulatoryService(serviceCategory, serviceSource) && outpatientRule != null) {
            return outpatientRule;
        }

        if (serviceCategory != null && !serviceCategory.isBlank()) {
            String normalizedServiceCategory = normalize(serviceCategory);

            InsuranceBenefitRule categoryRule =
                    normalizedRules.stream()
                            .filter(rule -> !rule.globalDefault())
                            .filter(rule -> !isCostBeneficiaryRule(rule))
                            .filter(rule -> matchesCategory(normalizedServiceCategory, rule))
                            .filter(rule -> matchesNetworkPreference(rule, preferInNetwork))
                            .filter(this::hasCopaymentValues)
                            .max(ruleComparator())
                            .orElse(null);

            if (categoryRule != null) {
                return categoryRule;
            }
        }

        if (outpatientRule != null) {
            return outpatientRule;
        }

        return selectPreferredDefaultRule(normalizedRules, preferInNetwork);
    }

    public InsuranceBenefitRule selectPreferredDefaultRule(
            List<InsuranceBenefitRule> rules,
            boolean preferInNetwork
    ) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        List<InsuranceBenefitRule> normalizedRules =
                rules.stream()
                        .map(InsuranceBenefitRuleNormalizer::normalize)
                        .toList();

        InsuranceBenefitRule explicitGlobal =
                normalizedRules.stream()
                        .filter(InsuranceBenefitRule::globalDefault)
                        .filter(this::hasCopaymentValues)
                        .max(ruleComparator())
                        .orElse(null);
        if (explicitGlobal != null) {
            return explicitGlobal;
        }

        InsuranceBenefitRule outpatientRule =
                findBestOutpatientRule(normalizedRules, preferInNetwork);
        if (outpatientRule != null) {
            return outpatientRule;
        }

        return normalizedRules.stream()
                .filter(rule -> !rule.globalDefault())
                .filter(rule -> !isCostBeneficiaryRule(rule))
                .filter(this::hasCopaymentValues)
                .filter(rule -> matchesNetworkPreference(rule, preferInNetwork))
                .max(ruleComparator())
                .orElse(null);
    }

    private InsuranceBenefitRule findBestOutpatientRule(
            List<InsuranceBenefitRule> rules,
            boolean preferInNetwork
    ) {
        return rules.stream()
                .filter(rule -> !rule.globalDefault())
                .filter(rule -> !isCostBeneficiaryRule(rule))
                .filter(this::hasCopaymentValues)
                .filter(this::isOutpatientRule)
                .filter(rule -> matchesNetworkPreference(rule, preferInNetwork))
                .max(ruleComparator())
                .orElse(null);
    }

    private Comparator<InsuranceBenefitRule> ruleComparator() {
        return Comparator.comparingInt(this::ruleSpecificityScore);
    }

    private boolean isAmbulatoryService(
            String serviceCategory,
            ServiceSource serviceSource
    ) {
        if (serviceSource == ServiceSource.ENCOUNTER_DEFAULT_SERVICE
                || serviceSource == ServiceSource.CONSULTATION_PORTAL
                || serviceSource == ServiceSource.SERVICE_AND_PRODUCT) {
            return true;
        }

        if (serviceCategory == null || serviceCategory.isBlank()) {
            return false;
        }

        String normalized = normalize(serviceCategory);
        return normalized.contains("outpatient")
                || normalized.contains("consult")
                || normalized.contains("gp")
                || normalized.contains("general practice")
                || normalized.contains("default")
                || normalized.contains("service");
    }

    private boolean isCostBeneficiaryRule(InsuranceBenefitRule rule) {
        return rule.benefitCategory() != null
                && rule.benefitCategory().equalsIgnoreCase("Cost Beneficiary");
    }

    private boolean isOutpatientRule(InsuranceBenefitRule rule) {
        String itemName = normalize(rule.itemName());
        String category = normalize(rule.benefitCategory());
        String providerType = normalize(rule.providerType());

        return providerType.contains("outpatient")
                || itemName.contains("outpatient")
                || category.contains("outpatient");
    }

    private boolean preferInNetwork(PatientInsurance insurance) {
        if (insurance == null) {
            return true;
        }

        String siteEligibility = normalize(insurance.getSiteEligibility());
        if (siteEligibility.contains("out")
                && siteEligibility.contains("network")) {
            return false;
        }

        return true;
    }

    private boolean matchesNetworkPreference(
            InsuranceBenefitRule rule,
            boolean preferInNetwork
    ) {
        String itemName = normalize(rule.itemName());

        if ("IN_NETWORK".equalsIgnoreCase(rule.networkType())) {
            return preferInNetwork;
        }
        if ("OUT_OF_NETWORK".equalsIgnoreCase(rule.networkType())) {
            return !preferInNetwork;
        }

        if (itemName.contains("out of network") || itemName.contains("out-of-network")) {
            return !preferInNetwork;
        }
        if (itemName.contains("in network") || itemName.contains("in-network")) {
            return preferInNetwork;
        }

        return preferInNetwork;
    }

    private int ruleSpecificityScore(InsuranceBenefitRule rule) {
        int score = 0;

        if (hasCopaymentPercent(rule)) {
            score += 10;
        }
        if (rule.patientMaximumCopayment() != null) {
            score += 4;
        }
        if (isInNetworkRule(rule)) {
            score += 8;
        }
        if (isOutpatientRule(rule)) {
            score += 8;
        }
        if (rule.itemName() != null && !rule.itemName().isBlank()) {
            score += 2;
        }

        return score;
    }

    private boolean isInNetworkRule(InsuranceBenefitRule rule) {
        if ("IN_NETWORK".equalsIgnoreCase(rule.networkType())) {
            return true;
        }

        String itemName = normalize(rule.itemName());
        return itemName.contains("in network") || itemName.contains("in-network");
    }

    private boolean hasCopaymentPercent(InsuranceBenefitRule rule) {
        return rule.patientCopaymentPercentage() != null
                && rule.patientCopaymentPercentage().signum() > 0;
    }

    private boolean hasCopaymentValues(InsuranceBenefitRule rule) {
        return hasCopaymentPercent(rule)
                || rule.patientMaximumCopayment() != null;
    }

    private boolean matchesCategory(
            String normalizedServiceCategory,
            InsuranceBenefitRule rule
    ) {
        String category = normalize(rule.benefitCategory());
        String itemName = normalize(rule.itemName());
        String itemCode = normalize(rule.itemCode());

        if (category.isEmpty() && itemName.isEmpty() && itemCode.isEmpty()) {
            return false;
        }

        if (!category.isEmpty()) {
            String strippedCategory = category.replace(".", " ").trim();
            if (normalizedServiceCategory.contains(strippedCategory)
                    || strippedCategory.contains(normalizedServiceCategory)) {
                return true;
            }
        }

        if (!itemName.isEmpty()
                && (normalizedServiceCategory.contains(itemName)
                        || itemName.contains(normalizedServiceCategory))) {
            return true;
        }

        return !itemCode.isEmpty()
                && normalizedServiceCategory.contains(itemCode);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}

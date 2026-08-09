package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class InsuranceBenefitRuleMatcher {

    public InsuranceBenefitRule resolveRule(
            List<InsuranceBenefitRule> rules,
            String serviceCategory,
            ServiceSource serviceSource
    ) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        InsuranceBenefitRule globalRule =
                rules.stream()
                        .filter(InsuranceBenefitRule::globalDefault)
                        .findFirst()
                        .orElse(null);

        if (isGpOrConsultationService(serviceCategory, serviceSource)) {
            InsuranceBenefitRule gpRule =
                    findByKeyword(rules, "gp", "general practice", "consult");
            if (gpRule != null) {
                return gpRule;
            }
        }

        if (serviceCategory != null && !serviceCategory.isBlank()) {
            String normalizedServiceCategory =
                    normalize(serviceCategory);

            for (InsuranceBenefitRule rule : rules) {
                if (rule.globalDefault()) {
                    continue;
                }

                if (matchesCategory(normalizedServiceCategory, rule)) {
                    return rule;
                }
            }
        }

        return globalRule;
    }

    private InsuranceBenefitRule findByKeyword(
            List<InsuranceBenefitRule> rules,
            String... keywords
    ) {
        for (InsuranceBenefitRule rule : rules) {
            if (rule.globalDefault()) {
                continue;
            }

            String category = normalize(rule.benefitCategory());
            String itemName = normalize(rule.itemName());

            for (String keyword : keywords) {
                if (category.contains(keyword) || itemName.contains(keyword)) {
                    return rule;
                }
            }
        }

        return null;
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

        if (!itemCode.isEmpty() && normalizedServiceCategory.contains(itemCode)) {
            return true;
        }

        return false;
    }

    private boolean isGpOrConsultationService(
            String serviceCategory,
            ServiceSource serviceSource
    ) {
        if (serviceSource == ServiceSource.CONSULTATION_PORTAL) {
            return true;
        }

        if (serviceCategory == null || serviceCategory.isBlank()) {
            return false;
        }

        String normalized = normalize(serviceCategory);
        return normalized.contains("consult")
                || normalized.contains("gp")
                || normalized.contains("general practice");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}

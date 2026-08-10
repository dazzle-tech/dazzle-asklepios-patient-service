package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;

import java.math.BigDecimal;

/**
 * Repairs persisted or extracted rules where patient copay caps were misclassified
 * as insurance maximum benefit (e.g. Copayment maximum per service = 75 stored as maximum_benefit).
 */
final class InsuranceBenefitRuleNormalizer {

    private static final BigDecimal PER_SERVICE_CAP_THRESHOLD =
            new BigDecimal("10000");

    private InsuranceBenefitRuleNormalizer() {
    }

    static InsuranceBenefitRule normalize(InsuranceBenefitRule rule) {
        if (rule == null) {
            return null;
        }

        BigDecimal copayCap = rule.patientMaximumCopayment();
        BigDecimal maximumBenefit = rule.maximumBenefit();
        BigDecimal copayPercent = rule.patientCopaymentPercentage();

        if (maximumBenefit == null) {
            return rule;
        }

        if (copayCap != null && copayCap.compareTo(maximumBenefit) == 0) {
            return copyRule(rule, null, copayCap);
        }

        if (copayCap == null
                && copayPercent != null
                && copayPercent.signum() > 0
                && isLikelyPerServiceCopayCap(maximumBenefit)) {
            return copyRule(rule, null, maximumBenefit);
        }

        return rule;
    }

    private static boolean isLikelyPerServiceCopayCap(BigDecimal value) {
        return value.signum() > 0
                && value.compareTo(PER_SERVICE_CAP_THRESHOLD) <= 0;
    }

    private static InsuranceBenefitRule copyRule(
            InsuranceBenefitRule rule,
            BigDecimal maximumBenefit,
            BigDecimal patientMaximumCopayment
    ) {
        return new InsuranceBenefitRule(
                rule.id(),
                rule.benefitCategory(),
                rule.itemName(),
                rule.itemCode(),
                rule.networkType(),
                rule.providerType(),
                rule.term(),
                rule.unit(),
                rule.currency(),
                maximumBenefit,
                rule.approvalLimit(),
                rule.patientCopaymentPercentage(),
                patientMaximumCopayment,
                rule.globalDefault(),
                rule.exceptionsJson()
        );
    }
}

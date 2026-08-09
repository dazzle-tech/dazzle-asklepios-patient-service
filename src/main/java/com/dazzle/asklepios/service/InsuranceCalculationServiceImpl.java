package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class InsuranceCalculationServiceImpl implements InsuranceCalculationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 4;

    @Override
    public InsuranceSplit calculateSplit(
            BigDecimal totalAmount,
            BigDecimal copaymentPercent,
            BigDecimal copaymentCap
    ) {
        BigDecimal normalizedNet = money(totalAmount);
        if (normalizedNet.signum() <= 0) {
            return new InsuranceSplit(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal copayPercent = percentage(copaymentPercent);
        BigDecimal patientShare =
                money(
                        normalizedNet
                                .multiply(copayPercent)
                                .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)
                );

        BigDecimal cap = money(copaymentCap);
        if (cap.signum() > 0) {
            patientShare = patientShare.min(cap);
        }

        patientShare = patientShare.min(normalizedNet);
        BigDecimal insuranceShare = money(normalizedNet.subtract(patientShare));

        return new InsuranceSplit(patientShare, insuranceShare);
    }

    @Override
    public InsuranceSplit calculateFromBenefitRule(
            BigDecimal totalAmount,
            InsuranceBenefitRule benefitRule,
            BigDecimal policyMaximumLimit
    ) {
        BigDecimal normalizedNet = money(totalAmount);
        if (normalizedNet.signum() <= 0) {
            return new InsuranceSplit(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        if (benefitRule == null) {
            return new InsuranceSplit(BigDecimal.ZERO, normalizedNet);
        }

        BigDecimal copayPercent = percentage(benefitRule.patientCopaymentPercentage());
        BigDecimal patientShare =
                money(
                        normalizedNet
                                .multiply(copayPercent)
                                .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)
                );

        BigDecimal maxCopay = money(benefitRule.patientMaximumCopayment());
        if (maxCopay.signum() > 0) {
            patientShare = patientShare.min(maxCopay);
        }

        patientShare = patientShare.min(normalizedNet);
        BigDecimal insuranceShare = money(normalizedNet.subtract(patientShare));

        BigDecimal maxBenefit = money(benefitRule.maximumBenefit());
        if (maxBenefit.signum() > 0) {
            insuranceShare = insuranceShare.min(maxBenefit);
        }

        BigDecimal approvalLimit = money(benefitRule.approvalLimit());
        if (approvalLimit.signum() > 0) {
            insuranceShare = insuranceShare.min(approvalLimit);
        }

        BigDecimal policyLimit = money(policyMaximumLimit);
        if (policyLimit.signum() > 0) {
            insuranceShare = insuranceShare.min(policyLimit);
        }

        insuranceShare = insuranceShare.max(BigDecimal.ZERO).min(normalizedNet);
        patientShare = money(normalizedNet.subtract(insuranceShare));

        return new InsuranceSplit(patientShare, insuranceShare);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}

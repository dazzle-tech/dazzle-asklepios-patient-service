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

        BigDecimal patientShare =
                calculatePatientCopayAmount(
                        normalizedNet,
                        copaymentPercent,
                        copaymentCap
                );

        BigDecimal insuranceShare =
                money(normalizedNet.subtract(patientShare));

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

        // 1) Patient copayment: percent + per-service maximum (Copayment maximum per service).
        BigDecimal patientShare =
                calculatePatientCopayAmount(
                        normalizedNet,
                        benefitRule.patientCopaymentPercentage(),
                        benefitRule.patientMaximumCopayment()
                );

        // 2) Insurance starts as the remainder after patient copay.
        BigDecimal insuranceShare =
                money(normalizedNet.subtract(patientShare));

        // 3) Insurance-side limits only (never applied to patient copay cap).
        insuranceShare =
                applyInsuranceSideLimits(
                        insuranceShare,
                        benefitRule.maximumBenefit(),
                        benefitRule.approvalLimit(),
                        policyMaximumLimit,
                        benefitRule.patientMaximumCopayment()
                );

        insuranceShare = insuranceShare.max(BigDecimal.ZERO).min(normalizedNet);

        // 4) If insurance is capped, the patient absorbs the uncovered amount.
        patientShare = money(normalizedNet.subtract(insuranceShare));

        return new InsuranceSplit(patientShare, insuranceShare);
    }

    /**
     * Patient responsibility from copayment percent with optional per-service cap.
     * Example: net=1000, 20%, cap=75 → patient=75 (not 200, not 925).
     */
    private BigDecimal calculatePatientCopayAmount(
            BigDecimal netAmount,
            BigDecimal copaymentPercent,
            BigDecimal copaymentMaximumPerService
    ) {
        BigDecimal copayPercent = percentage(copaymentPercent);
        BigDecimal patientAmount =
                money(
                        netAmount
                                .multiply(copayPercent)
                                .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)
                );

        BigDecimal patientCap = money(copaymentMaximumPerService);
        if (patientCap.signum() > 0) {
            patientAmount = patientAmount.min(patientCap);
        }

        return patientAmount.min(netAmount);
    }

    /**
     * Caps insurance share only. Maximum benefit and approval limits never replace copayment maximum.
     */
    private BigDecimal applyInsuranceSideLimits(
            BigDecimal insuranceShare,
            BigDecimal maximumBenefit,
            BigDecimal approvalLimit,
            BigDecimal policyMaximumLimit,
            BigDecimal patientCopaymentMaximumPerService
    ) {
        BigDecimal capped = money(insuranceShare);

        BigDecimal insuranceMaximumBenefit = money(maximumBenefit);
        BigDecimal patientCopayCap = money(patientCopaymentMaximumPerService);
        if (insuranceMaximumBenefit.signum() > 0
                && (patientCopayCap.signum() <= 0
                        || insuranceMaximumBenefit.compareTo(patientCopayCap) > 0)) {
            capped = capped.min(insuranceMaximumBenefit);
        }

        BigDecimal insuranceApprovalLimit = money(approvalLimit);
        if (insuranceApprovalLimit.signum() > 0) {
            capped = capped.min(insuranceApprovalLimit);
        }

        BigDecimal policyLimit = money(policyMaximumLimit);
        if (policyLimit.signum() > 0
                && (patientCopayCap.signum() <= 0
                        || policyLimit.compareTo(patientCopayCap) > 0)) {
            capped = capped.min(policyLimit);
        }

        return capped;
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

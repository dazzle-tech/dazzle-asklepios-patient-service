package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPricingSnapshotStatus;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;
import com.dazzle.asklepios.repository.BillingPricingSnapshotRepository;
import com.dazzle.asklepios.service.dto.billing.TaxShareRule;
import com.dazzle.asklepios.service.dto.billing.TaxShareSplit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single place for the tax setup share flags (Patient Copayment / Claim Share).
 *
 * Both flags on: original calculation, the tax-inclusive net is split
 * between patient and insurance exactly as before.
 *
 * Otherwise the amount before tax is split, and each share is taxed only
 * when its own flag is on (percentage of that share, or the fixed amount
 * added to it). A share whose flag is off carries no tax, and its tax is
 * never moved to the other share.
 */
@Service
@RequiredArgsConstructor
public class TaxShareCalculator {

    private static final int MONEY_SCALE = 4;

    private final InsurancePatientShareCalculator
            insurancePatientShareCalculator;

    private final BillingPricingSnapshotRepository
            billingPricingSnapshotRepository;

    /**
     * Splits an insurance item.
     *
     * @param netAmount item net, including taxAmount
     * @param taxAmount item tax included in netAmount
     */
    public TaxShareSplit split(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal netAmount,
            BigDecimal taxAmount,
            TaxShareRule rule,
            VisitMaxLimitTracker visitMaxLimitTracker
    ) {
        BigDecimal net = money(netAmount);
        BigDecimal tax = money(taxAmount);

        if (rule == null
                || !rule.splitsByShare()
                || !isInsuranceBilled(insurance, item)) {
            BigDecimal patientShare =
                    patientShareOf(
                            insurance,
                            item,
                            net,
                            visitMaxLimitTracker
                    );

            return new TaxShareSplit(
                    patientShare,
                    money(net.subtract(patientShare)),
                    tax,
                    net,
                    null,
                    null
            );
        }

        BigDecimal baseAmount =
                money(net.subtract(tax).max(BigDecimal.ZERO));

        BigDecimal patientBase =
                patientShareOf(
                        insurance,
                        item,
                        baseAmount,
                        visitMaxLimitTracker
                );

        BigDecimal insuranceBase =
                money(baseAmount.subtract(patientBase));

        BigDecimal patientTax =
                rule.onPatientCopayment()
                        ? shareTax(rule, patientBase)
                        : zero();

        BigDecimal insuranceTax =
                rule.onClaimShare()
                        ? shareTax(rule, insuranceBase)
                        : zero();

        BigDecimal appliedTax =
                money(patientTax.add(insuranceTax));

        return new TaxShareSplit(
                money(patientBase.add(patientTax)),
                money(insuranceBase.add(insuranceTax)),
                appliedTax,
                money(baseAmount.add(appliedTax)),
                patientTax,
                insuranceTax
        );
    }

    /**
     * Rule of a billed line, from its active pricing snapshot.
     * Lines without a snapshot keep the original calculation.
     */
    public TaxShareRule ruleForChargeLine(
            BillingChargeLine chargeLine
    ) {
        if (chargeLine == null || chargeLine.getId() == null) {
            return TaxShareRule.BOTH_SHARES;
        }

        return billingPricingSnapshotRepository
                .findTopByChargeLine_IdAndStatusOrderByIdDesc(
                        chargeLine.getId(),
                        BillingPricingSnapshotStatus.ACTIVE
                )
                .map(snapshot -> ruleOf(snapshot, chargeLine))
                .orElse(TaxShareRule.BOTH_SHARES);
    }

    /**
     * The snapshot has no fixed amount; a fixed tax that was split by
     * share is already held by the line (it sits on the flagged share).
     */
    public TaxShareRule ruleOf(
            BillingPricingSnapshot snapshot,
            BillingChargeLine chargeLine
    ) {
        if (snapshot == null) {
            return TaxShareRule.BOTH_SHARES;
        }

        return new TaxShareRule(
                !Boolean.FALSE.equals(snapshot.getTaxOnPatientCopayment()),
                !Boolean.FALSE.equals(snapshot.getTaxOnClaimShare()),
                snapshot.getTaxType(),
                snapshot.getTaxRate(),
                snapshot.getTaxType() == TaxType.FIXED_AMOUNT
                        && chargeLine != null
                        ? money(chargeLine.getTaxAmount())
                        : null
        );
    }

    /**
     * Item tax as the pricing engine calculates it (before the share flags),
     * rebuilt from a billed line whose tax was split by share.
     */
    public BigDecimal pricedTaxOf(
            BillingChargeLine chargeLine,
            BillingPricingSnapshot snapshot
    ) {
        BigDecimal lineTax = money(chargeLine.getTaxAmount());
        TaxShareRule rule = ruleOf(snapshot, chargeLine);

        if (snapshot == null || !rule.splitsByShare()) {
            return lineTax;
        }

        if (rule.taxType() == TaxType.FIXED_AMOUNT) {
            /*
             * Same as BillingPricingService for a fixed tax.
             */
            return money(defaultZero(snapshot.getTaxRate()));
        }

        BigDecimal baseAmount =
                money(chargeLine.getNetAmount()).subtract(lineTax);

        return baseAmount
                .multiply(defaultZero(snapshot.getTaxRate()))
                .divide(
                        BigDecimal.valueOf(100),
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );
    }

    /**
     * Stores the share taxes on the line (null = original calculation).
     */
    public void recordShareTax(
            BillingChargeLine chargeLine,
            TaxShareSplit split
    ) {
        if (chargeLine == null) {
            return;
        }

        boolean byShare = split != null && split.splitsByShare();

        chargeLine.setPatientTaxAmount(
                byShare ? split.patientTaxAmount() : null
        );
        chargeLine.setInsuranceTaxAmount(
                byShare ? split.insuranceTaxAmount() : null
        );
    }

    public void clearShareTax(
            BillingChargeLine chargeLine
    ) {
        recordShareTax(chargeLine, null);
    }

    /**
     * Tax carried by the patient share (insuranceShare = false) or the
     * insurance share of a line. Null when the line uses the original
     * calculation (callers keep their proportional split).
     */
    public BigDecimal shareTaxOf(
            BillingChargeLine chargeLine,
            boolean insuranceShare
    ) {
        if (chargeLine == null
                || chargeLine.getPatientTaxAmount() == null
                || chargeLine.getInsuranceTaxAmount() == null) {
            return null;
        }

        BigDecimal patientTax = money(chargeLine.getPatientTaxAmount());
        BigDecimal insuranceTax = money(chargeLine.getInsuranceTaxAmount());

        if (patientTax.add(insuranceTax)
                .compareTo(money(chargeLine.getTaxAmount())) != 0) {
            return null;
        }

        return insuranceShare ? insuranceTax : patientTax;
    }

    /**
     * Gross / discount / tax of one share of a line, for invoice and note
     * lines. Null when the line uses the original calculation, or when the
     * share amount is not the line responsibility of that share.
     */
    public ShareAmounts shareAmountsOf(
            BillingChargeLine chargeLine,
            BigDecimal shareAmount,
            boolean insuranceShare
    ) {
        BigDecimal shareTax = shareTaxOf(chargeLine, insuranceShare);

        if (shareTax == null) {
            return null;
        }

        BigDecimal share = money(shareAmount);
        BigDecimal responsibility =
                money(
                        insuranceShare
                                ? chargeLine.getInsuranceResponsibilityAmount()
                                : chargeLine.getPatientResponsibilityAmount()
                );

        if (share.compareTo(responsibility) != 0) {
            return null;
        }

        BigDecimal lineTax = money(chargeLine.getTaxAmount());
        BigDecimal lineBase =
                money(chargeLine.getNetAmount()).subtract(lineTax);
        BigDecimal shareBase =
                share.subtract(shareTax).max(BigDecimal.ZERO);

        return new ShareAmounts(
                proportional(chargeLine.getGrossAmount(), shareBase, lineBase),
                proportional(chargeLine.getDiscountAmount(), shareBase, lineBase),
                shareTax
        );
    }

    /**
     * Cash billing (no insurance, coverage not in force, or an uncovered
     * item billed as cash) keeps the full item tax.
     */
    private boolean isInsuranceBilled(
            PatientInsurance insurance,
            PatientServiceAndProduct item
    ) {
        return insurance != null
                && insurance.getId() != null
                && (item == null || !item.isUncoveredCashItem())
                && insurancePatientShareCalculator
                        .isLatestCoverageInForce(insurance);
    }

    public record ShareAmounts(
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount
    ) {
    }

    private BigDecimal patientShareOf(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal amount,
            VisitMaxLimitTracker visitMaxLimitTracker
    ) {
        return money(
                insurancePatientShareCalculator
                        .calculateSplit(
                                insurance,
                                item,
                                amount,
                                visitMaxLimitTracker
                        )
                        .patientShare()
        );
    }

    private BigDecimal shareTax(
            TaxShareRule rule,
            BigDecimal shareAmount
    ) {
        if (shareAmount.signum() <= 0) {
            return zero();
        }

        if (rule.taxType() == TaxType.FIXED_AMOUNT) {
            return money(defaultZero(rule.taxFixedAmount()));
        }

        return shareAmount
                .multiply(defaultZero(rule.taxRate()))
                .divide(
                        BigDecimal.valueOf(100),
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal proportional(
            BigDecimal amount,
            BigDecimal share,
            BigDecimal whole
    ) {
        if (amount == null
                || share == null
                || whole == null
                || whole.signum() == 0) {
            return zero();
        }

        return money(
                amount.multiply(share)
                        .divide(whole, MONEY_SCALE, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return defaultZero(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.InsuranceSplit;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Visit-level max-limit pool. Patient share is capped across the whole
 * encounter, not once per service.
 */
public final class VisitMaxLimitTracker {

    private static final int MONEY_SCALE = 4;

    private final BigDecimal visitMaxLimit;
    private BigDecimal remaining;

    private VisitMaxLimitTracker(BigDecimal visitMaxLimit, BigDecimal remaining) {
        this.visitMaxLimit = visitMaxLimit;
        this.remaining = remaining;
    }

    public static VisitMaxLimitTracker unbounded() {
        return new VisitMaxLimitTracker(null, null);
    }

    public static VisitMaxLimitTracker of(BigDecimal visitMaxLimit) {
        BigDecimal normalized = money(visitMaxLimit);
        if (normalized.signum() <= 0) {
            return unbounded();
        }

        return new VisitMaxLimitTracker(normalized, normalized);
    }

    public static VisitMaxLimitTracker withConsumed(
            BigDecimal visitMaxLimit,
            BigDecimal alreadyConsumedPatientShare
    ) {
        VisitMaxLimitTracker tracker = of(visitMaxLimit);
        if (!tracker.hasLimit()) {
            return tracker;
        }

        BigDecimal consumed = money(alreadyConsumedPatientShare).max(BigDecimal.ZERO);
        tracker.remaining = tracker.visitMaxLimit.subtract(consumed).max(BigDecimal.ZERO);
        return tracker;
    }

    public boolean hasLimit() {
        return visitMaxLimit != null && visitMaxLimit.signum() > 0;
    }

    public BigDecimal remaining() {
        return remaining == null
                ? null
                : remaining.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public InsuranceSplit consume(InsuranceSplit split, BigDecimal netAmount) {
        return consume(split, netAmount, BigDecimal.ONE);
    }

    /**
     * The pool caps what the patient actually pays. When only part of the
     * patient share is charged to the patient (its tax portion removed by the
     * Patient Copayment toggle), only that payable part,
     * {@code patientShare * payableRatio}, is drawn from the pool.
     */
    public InsuranceSplit consume(
            InsuranceSplit split,
            BigDecimal netAmount,
            BigDecimal payableRatio
    ) {
        if (split == null || !hasLimit()) {
            return split;
        }

        BigDecimal ratio = normalizeRatio(payableRatio);
        BigDecimal normalizedNet = money(netAmount);
        BigDecimal requestedPatient = money(split.patientShare());
        BigDecimal maxPatientShare =
                ratio.compareTo(BigDecimal.ONE) == 0
                        ? remaining
                        : remaining.divide(ratio, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal patientShare = requestedPatient.min(maxPatientShare).min(normalizedNet);
        if (patientShare.signum() < 0) {
            patientShare = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal payablePatientShare = money(patientShare.multiply(ratio));
        remaining = remaining.subtract(payablePatientShare).max(BigDecimal.ZERO);
        BigDecimal insuranceShare = money(normalizedNet.subtract(patientShare));

        return new InsuranceSplit(patientShare, insuranceShare);
    }

    private static BigDecimal normalizeRatio(BigDecimal payableRatio) {
        if (payableRatio == null
                || payableRatio.signum() <= 0
                || payableRatio.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }

        return payableRatio;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}

package com.dazzle.asklepios.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decides whether a visit line crosses the pre-approval ceiling.
 *
 * Fixed: the ceiling is the entered amount.
 * Percentage: the ceiling is that percent of the coverage-limit amount when the
 * contract has a fixed coverage limit. Without that base, the ceiling is the
 * same percent of the running visit total.
 */
public final class PreApprovalLimitEvaluator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 4;

    private PreApprovalLimitEvaluator() {
    }

    public static boolean exceeds(
            String valueType,
            BigDecimal limitValue,
            BigDecimal consumed,
            BigDecimal current,
            BigDecimal percentageBase
    ) {
        if (limitValue == null || current == null || current.signum() <= 0) {
            return false;
        }
        BigDecimal posted = money(consumed).add(money(current));
        return posted.compareTo(ceiling(valueType, limitValue, posted, percentageBase)) > 0;
    }

    public static BigDecimal ceiling(
            String valueType,
            BigDecimal limitValue,
            BigDecimal runningTotal,
            BigDecimal percentageBase
    ) {
        if (!"PERCENTAGE".equalsIgnoreCase(valueType)) {
            return money(limitValue);
        }
        BigDecimal base = percentageBase != null && percentageBase.signum() > 0
                ? percentageBase
                : runningTotal;
        return money(base.multiply(limitValue).divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}

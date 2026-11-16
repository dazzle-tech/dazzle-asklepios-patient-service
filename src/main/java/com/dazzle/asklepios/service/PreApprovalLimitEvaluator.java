package com.dazzle.asklepios.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decides whether one item reaches its own pre-approval limit.
 * Earlier items on the visit are not added together.
 *
 * Fixed: the ceiling is the entered amount.
 * Percentage: the ceiling is that percent of the coverage-limit amount when the
 * contract has a fixed coverage limit. Without that base, the entered number is
 * the item amount that must be reached.
 * An item at the ceiling is included, not only an item above it.
 */
public final class PreApprovalLimitEvaluator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 4;

    private PreApprovalLimitEvaluator() {
    }

    public static boolean exceeds(
            String valueType,
            BigDecimal limitValue,
            BigDecimal itemAmount,
            BigDecimal percentageBase
    ) {
        if (limitValue == null || itemAmount == null || itemAmount.signum() <= 0) {
            return false;
        }
        return money(itemAmount).compareTo(ceiling(valueType, limitValue, percentageBase)) >= 0;
    }

    public static BigDecimal ceiling(
            String valueType,
            BigDecimal limitValue,
            BigDecimal percentageBase
    ) {
        if (!"PERCENTAGE".equalsIgnoreCase(valueType)
                || percentageBase == null
                || percentageBase.signum() <= 0) {
            return money(limitValue);
        }
        return money(percentageBase.multiply(limitValue).divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}

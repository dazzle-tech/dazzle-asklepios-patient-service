package com.dazzle.asklepios.integration.waseel.service.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Waseel BV-01156: item factor must be greater than 0 and only
 * {@code 0.N}, {@code 0.NN}, {@code 0.NNN}, or {@code 1}.
 */
public final class WaseelFactorFormatter {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int MAX_SCALE = 3;

    private WaseelFactorFormatter() {
    }

    public static BigDecimal format(BigDecimal factor) {
        if (factor == null || factor.signum() <= 0 || factor.compareTo(ONE) >= 0) {
            return ONE;
        }

        BigDecimal rounded = factor.setScale(MAX_SCALE, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0) {
            return ONE;
        }
        if (rounded.compareTo(ONE) >= 0) {
            return ONE;
        }

        return new BigDecimal(rounded.stripTrailingZeros().toPlainString());
    }
}

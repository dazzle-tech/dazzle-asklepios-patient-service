package com.dazzle.asklepios.integration.waseel.service.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Waseel BV-01156: item factor must be greater than 0 and only
 * {@code 0.N}, {@code 0.NN}, {@code 0.NNN}, or {@code 1}.
 */
public final class WaseelFactorNormalizer {

    private static final int MAX_SCALE = 3;

    private WaseelFactorNormalizer() {}

    public static BigDecimal fromGrossAndDiscount(BigDecimal gross, BigDecimal discount) {
        if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }

        if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }

        BigDecimal discountRatio = discount.divide(gross, MAX_SCALE + 3, RoundingMode.HALF_UP);
        return normalize(BigDecimal.ONE.subtract(discountRatio));
    }

    public static BigDecimal normalize(BigDecimal factor) {
        if (factor == null || factor.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }

        if (factor.compareTo(BigDecimal.ONE) >= 0) {
            return BigDecimal.ONE;
        }

        BigDecimal rounded = factor.setScale(MAX_SCALE, RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.ONE) >= 0) {
            return BigDecimal.ONE;
        }

        if (rounded.signum() <= 0) {
            return BigDecimal.ONE;
        }

        return new BigDecimal(rounded.stripTrailingZeros().toPlainString());
    }
}

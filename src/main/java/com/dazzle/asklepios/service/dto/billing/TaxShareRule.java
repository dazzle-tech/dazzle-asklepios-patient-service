package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.billing.TaxType;

import java.math.BigDecimal;

/**
 * Tax setup flags of an item tax (insurance visits).
 *
 * Patient Copayment + Claim Share: original calculation (the tax-inclusive
 * net is split between patient and insurance).
 *
 * Otherwise each share is taxed only when its own flag is on
 * (PERCENTAGE: share amount x rate, FIXED_AMOUNT: the fixed amount).
 * A share whose flag is off carries no tax, and its tax is never moved
 * to the other share.
 */
public record TaxShareRule(
        boolean onPatientCopayment,
        boolean onClaimShare,
        TaxType taxType,
        BigDecimal taxRate,
        BigDecimal taxFixedAmount
) {

    public static final TaxShareRule BOTH_SHARES =
            new TaxShareRule(true, true, null, null, null);

    public static TaxShareRule of(
            BillingPricingInput input
    ) {
        if (input == null) {
            return BOTH_SHARES;
        }

        return new TaxShareRule(
                !Boolean.FALSE.equals(input.taxOnPatientCopayment()),
                !Boolean.FALSE.equals(input.taxOnClaimShare()),
                input.taxType(),
                input.taxRate(),
                input.taxFixedAmount()
        );
    }

    /**
     * True when the tax is calculated per share by the flags,
     * false when the original calculation applies.
     */
    public boolean splitsByShare() {
        return !(onPatientCopayment && onClaimShare)
                && (taxType == TaxType.PERCENTAGE
                || taxType == TaxType.FIXED_AMOUNT);
    }
}

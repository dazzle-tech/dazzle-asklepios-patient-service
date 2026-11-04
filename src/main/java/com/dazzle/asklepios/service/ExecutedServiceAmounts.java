package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.PriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;

import java.math.BigDecimal;

/**
 * Stored executed prices. Each service keeps the amount resolved from its own
 * price source; nothing here calls pricing again.
 */
public final class ExecutedServiceAmounts {

    private ExecutedServiceAmounts() {
    }

    public static boolean isCancelled(PaymentStatus status) {
        return status == PaymentStatus.CANCELLED;
    }

    public static boolean isCancelled(BillingChargeLineStatus status) {
        return status == BillingChargeLineStatus.CANCELLED;
    }

    public static BigDecimal net(
            BigDecimal netAmount,
            BigDecimal unitPrice,
            BigDecimal quantity
    ) {
        if (netAmount != null) {
            return netAmount;
        }

        BigDecimal unit = zeroIfNull(unitPrice);
        BigDecimal qty = quantity == null || quantity.signum() <= 0
                ? BigDecimal.ONE
                : quantity;
        return unit.multiply(qty);
    }

    /**
     * When a split was never stored, the executed amount is the patient's.
     */
    public static BigDecimal patientShare(
            BigDecimal patientShare,
            BigDecimal insuranceShare,
            BigDecimal executedNet
    ) {
        BigDecimal patient = zeroIfNull(patientShare);
        BigDecimal insurance = zeroIfNull(insuranceShare);
        if (patient.signum() == 0 && insurance.signum() == 0) {
            return zeroIfNull(executedNet);
        }
        return patient;
    }

    public static BigDecimal insuranceShare(BigDecimal insuranceShare) {
        return zeroIfNull(insuranceShare);
    }

    public static String priceSource(PriceSource source) {
        if (source == null) {
            return null;
        }

        return switch (source) {
            case PRICE_LIST, INSURANCE_CONTRACT, WASEEL_SBS ->
                    BillingPriceSource.PRICE_LIST.name();
            default -> BillingPriceSource.SETUP_FALLBACK.name();
        };
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

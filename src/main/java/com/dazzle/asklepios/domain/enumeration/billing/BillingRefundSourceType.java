package com.dazzle.asklepios.domain.enumeration.billing;

public enum BillingRefundSourceType {

    /**
     * Refund tied to a known original payment.
     */
    ORIGINAL_PAYMENT,

    /**
     * Refund from the patient's currently available wallet balance.
     */
    WALLET_AVAILABLE,

    /**
     * Refund due to an overpayment.
     */
    OVERPAYMENT,

    /**
     * Manual finance adjustment.
     */
    MANUAL_ADJUSTMENT
}
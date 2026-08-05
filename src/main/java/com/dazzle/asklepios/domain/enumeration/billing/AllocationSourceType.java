package com.dazzle.asklepios.domain.enumeration.billing;

/**
 * Identifies the financial source used to settle
 * a billing responsibility.
 */
public enum AllocationSourceType {

    /**
     * Allocation created by consuming an advance-payment reservation.
     */
    RESERVATION,

    /**
     * Direct allocation from a confirmed payment
     * without using a reservation.
     */
    PAYMENT,

    /**
     * Allocation created by consuming available patient wallet balance.
     */
    WALLET_AVAILABLE,

    /**
     * Allocation covered by creating patient debit.
     */
    DEBIT,

    /**
     * Allocation assigned to an insurance payer.
     */
    INSURANCE,

    /**
     * Allocation assigned to another external payer.
     */
    OTHER_PAYER,

    /**
     * Manual allocation performed by an authorized finance user.
     */
    MANUAL_ADJUSTMENT
}
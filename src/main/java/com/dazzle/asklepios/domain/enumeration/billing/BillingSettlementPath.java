package com.dazzle.asklepios.domain.enumeration.billing;

/**
 * Describes where a billable item lands in the accounting flow once its
 * billing trigger fires.
 */
public enum BillingSettlementPath {

    /**
     * Charge is created immediately and the patient share appears in
     * remaining to pay / encounter outstanding balance.
     */
    REMAINING_TO_PAY,

    /**
     * Charge is deferred until checkout; unpaid balance may be posted to
     * patient ledger debit during checkout settlement.
     */
    LEDGER_DEBIT_AT_CHECKOUT,

    /**
     * Billing requires an explicit manual prepare/collect action.
     */
    MANUAL
}

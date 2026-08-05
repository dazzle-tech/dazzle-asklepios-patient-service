package com.dazzle.asklepios.domain.enumeration.billing;

public enum BillingPricingSnapshotStatus {

    /**
     * Current snapshot used by the active charge line.
     */
    ACTIVE,

    /**
     * Old snapshot after the service was repriced.
     */
    SUPERSEDED,

    /**
     * Snapshot cancelled because the charge line was cancelled.
     */
    CANCELLED

}
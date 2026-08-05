package com.dazzle.asklepios.integration.waseel.event;

import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;

/**
 * Schedules encounter pre-authorization sync/submit after the current transaction commits.
 */
public record EncounterPreAuthorizationSyncEvent(
        Long encounterId,
        BillingCoverageType coverageType
) {
    public EncounterPreAuthorizationSyncEvent(Long encounterId) {
        this(encounterId, null);
    }
}

package com.dazzle.asklepios.domain.enumeration.waseelIntegration;

/**
 * An invoice stays ACTIVE under one statement until its claim is rejected or returned.
 * RELEASED rows are kept so the original statement and the failure reason remain auditable.
 */
public enum ClaimSettlementMembershipStatus {
    ACTIVE,
    RELEASED
}

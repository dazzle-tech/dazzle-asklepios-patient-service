package com.dazzle.asklepios.domain.enumeration.eligibility;

/** The two limits of the eligibility contract, like Coverage Management pre-approval and coverage limit. */
public enum EligibilityLimitType {
    /** A single service above this amount needs pre-authorization. */
    APPROVAL_LIMIT,
    /** The insurance pays at most this amount within the period. */
    MAXIMUM_BENEFIT
}

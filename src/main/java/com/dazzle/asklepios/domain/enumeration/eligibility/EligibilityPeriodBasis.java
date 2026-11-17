package com.dazzle.asklepios.domain.enumeration.eligibility;

import java.util.Locale;

/**
 * The period a benefit is counted over, read from the benefit "term", e.g.
 * "Annual, renewing on the anniversary" or "For the total term, lifetime, of the policy or coverage".
 */
public enum EligibilityPeriodBasis {
    PER_ENCOUNTER,
    PER_DAY,
    /** One policy year, renewing on the anniversary of the benefit start date. */
    POLICY_YEAR,
    /** The whole benefit period of the policy. */
    POLICY_TERM;

    /** A term the policy does not state is counted over the whole benefit period. */
    public static EligibilityPeriodBasis fromTerm(String term) {
        String text = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        if (text.contains("annual") || text.contains("anniversary") || text.contains("year")) {
            return POLICY_YEAR;
        }
        if (text.contains("per day") || text.contains("daily") || text.equals("day")) {
            return PER_DAY;
        }
        if (text.contains("visit") || text.contains("encounter") || text.contains("incident")) {
            return PER_ENCOUNTER;
        }
        return POLICY_TERM;
    }
}

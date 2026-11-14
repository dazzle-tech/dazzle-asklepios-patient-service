package com.dazzle.asklepios.domain.enumeration.eligibility;

/**
 * Which services a benefit of the patient eligibility contract applies to,
 * read from the benefit category, name and description returned by the payer.
 */
public enum EligibilityBenefitScope {
    /** The whole policy (e.g. "Policy Maximum Annual Limit and Approval limit", policy copay). */
    POLICY,
    /** Outpatient treatment (e.g. "Outpatient Treatment expenses-within minimum unified network"). */
    OUTPATIENT,
    /** Inpatient treatment, room and board. */
    INPATIENT,
    /** Dental treatment. */
    DENTAL,
    /** Vision and optical. */
    VISION,
    /** Prescribed medicines. */
    MEDICATION,
    /** Maternity and pregnancy. */
    MATERNITY,
    /** Any other benefit; stored as returned, not applied to a service type. */
    OTHER
}

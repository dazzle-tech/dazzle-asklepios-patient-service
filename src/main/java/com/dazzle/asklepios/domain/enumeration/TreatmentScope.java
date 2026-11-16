package com.dazzle.asklepios.domain.enumeration;

/**
 * Declared treatment area for a planned-treatment row.
 * Legacy rows with a null scope are treated as {@link #SPECIFIC_TOOTH}.
 */
public enum TreatmentScope {
    SPECIFIC_TOOTH,
    UPPER_ARCH,
    LOWER_ARCH,
    FULL_MOUTH
}

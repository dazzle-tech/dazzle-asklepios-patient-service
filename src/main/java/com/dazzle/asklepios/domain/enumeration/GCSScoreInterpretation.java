package com.dazzle.asklepios.domain.enumeration;

/**
 * GCS score interpretation.
 *
 * WARNING: do NOT reorder, insert in the middle, or remove values.
 * GlasgowComaScaleAssessment.scoreInterpretation is stored by ORDINAL (position) in the database
 * (0 = MILD, 1 = MODERATE, 2 = SEVERE, 3 = NORMAL), so changing the order would make existing
 * records read as a different interpretation. New values must be added at the END only.
 */
public enum GCSScoreInterpretation {

    MILD_TRAUMATIC_BRAIN_INJURY,

    MODERATE_TRAUMATIC_BRAIN_INJURY,

    SEVERE_TRAUMATIC_BRAIN_INJURY_COMA,

    NORMAL

}
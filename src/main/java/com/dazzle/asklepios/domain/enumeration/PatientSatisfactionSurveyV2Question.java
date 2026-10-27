package com.dazzle.asklepios.domain.enumeration;

/**
 * The questions of the patient satisfaction survey v2: the ref from the survey sheet followed by
 * the construct it measures, so a stored answer stays readable even if the question is dropped.
 * The wording lives in the UI; this list only checks what a submit may contain.
 */
public enum PatientSatisfactionSurveyV2Question {
    C1_ACCESS_TO_CARE,
    C2_DELAY_COMMUNICATION,
    C3_PERCEIVED_WAIT,
    C4_CLINICIAN_COMMUNICATION,
    C5_SHARED_DECISION_MAKING,
    C6_LOYALTY,
    E1_PRIVACY,
    E2_SAFETY_PRACTICE,
    E3_CLEANLINESS,
    C7_OPEN_FEEDBACK(true),
    RS1_RESULTS_TURNAROUND,
    RS2_RESULTS_COMMUNICATION,
    BL1_PRICE_TRANSPARENCY,
    LB1_PHLEBOTOMY_COMPETENCE,
    LB2_LAB_WAIT,
    RD1_PRE_PROCEDURE_EXPLANATION,
    RD2_RADIOLOGY_WAIT,
    PH1_FORMULARY_AVAILABILITY,
    PH2_PHARMACY_WAIT,
    NU1_NURSING_INTERPERSONAL_QUALITY,
    NU2_NURSING_COMMUNICATION,
    RG1_RECEPTION_MANNER,
    RG2_REGISTRATION_PROCESS;

    private final boolean freeText;

    PatientSatisfactionSurveyV2Question() {
        this(false);
    }

    PatientSatisfactionSurveyV2Question(boolean freeText) {
        this.freeText = freeText;
    }

    public boolean isFreeText() {
        return freeText;
    }
}

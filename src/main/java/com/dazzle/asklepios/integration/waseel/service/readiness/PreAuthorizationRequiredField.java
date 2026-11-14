package com.dazzle.asklepios.integration.waseel.service.readiness;

/**
 * A field Waseel needs before a pre-authorization can be sent (GapSheet 3.2.1, "MDS IMPORTANT").
 * {@link #category()} is the NPHIES supporting-info category; {@link #section()} tells the user where to fill it.
 */
public enum PreAuthorizationRequiredField {

    DIAGNOSIS(null, "Diagnosis", "Diagnosis"),
    CHIEF_COMPLAINT("chief-complaint", "Chief complaint", "Chief complaint"),
    HISTORY_OF_PRESENT_ILLNESS("history-of-present-illness", "History of present illness", "Chief complaint"),
    PATIENT_HISTORY("patient-history", "Patient history", "Patient history"),
    PHYSICAL_EXAMINATION("physical-examination", "Physical examination", "Physical examination"),
    TREATMENT_PLAN("treatment-plan", "Treatment plan", "Assessment / plan"),
    INVESTIGATION_RESULT("investigation-result", "Investigation result", "Investigations"),
    TEMPERATURE("temperature", "Temperature", "Vital signs"),
    PULSE("pulse", "Pulse", "Vital signs"),
    OXYGEN_SATURATION("oxygen-saturation", "Oxygen saturation", "Vital signs"),
    RESPIRATORY_RATE("respiratory-rate", "Respiratory rate", "Vital signs"),
    SYSTOLIC("vital-sign-systolic", "Blood pressure (systolic)", "Vital signs"),
    DIASTOLIC("vital-sign-diastolic", "Blood pressure (diastolic)", "Vital signs"),
    HEIGHT("vital-sign-height", "Height", "Body measurements"),
    WEIGHT("vital-sign-weight", "Weight", "Body measurements");

    private final String category;
    private final String label;
    private final String section;

    PreAuthorizationRequiredField(String category, String label, String section) {
        this.category = category;
        this.label = label;
        this.section = section;
    }

    /** NPHIES supporting-info category, or {@code null} for the diagnosis. */
    public String category() {
        return category;
    }

    public String label() {
        return label;
    }

    public String section() {
        return section;
    }
}

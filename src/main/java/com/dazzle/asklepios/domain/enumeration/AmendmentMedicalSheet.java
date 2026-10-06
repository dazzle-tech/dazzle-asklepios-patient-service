package com.dazzle.asklepios.domain.enumeration;

public enum AmendmentMedicalSheet {
    PROGRESS_NOTES("Progress Notes"),
    VITAL_SIGNS("Vital Signs"),
    CONSULTATION("Consultation"),
    ENCOUNTER_NARRATIVE("Encounter Narrative"),
    ASSESSMENT("Assessment"),
    PLAN("Plan"),
    ALLERGIES("Allergies"),
    MEDICAL_WARNINGS("Medical Warnings"),
    DIAGNOSIS("Diagnosis"),
    REVIEW_OF_SYSTEMS("Review Of Systems"),
    OBSERVATIONS_COMPLAINTS("Observations / Complaints"),
    PAIN_ASSESSMENT("Pain Assessment"),
    BODY_MEASUREMENTS("Body Measurements"),
    ADDITIONAL_MEASUREMENTS("Additional Measurements"),
    PRESCRIPTION("Prescription"),
    PRESCRIPTION_MEDICATION("Prescription Medication"),
    REFERRAL("Referral"),
    VACCINATION("Vaccination"),
    DENTAL_PROCEDURES("Dental Procedures"),
    GLASGOW("Glasgow Coma Scale"),
    FLACC("FLACC Pain Scale"),
    TELEPHONIC_CONSULTATION("Telephonic Consultation"),
    GENERAL_ASSESSMENT("General Assessment"),
    SICK_LEAVE("Sick Leave");

    private final String label;

    AmendmentMedicalSheet(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

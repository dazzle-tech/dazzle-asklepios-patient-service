package com.dazzle.asklepios.integration.waseel.service.readiness;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.CHIEF_COMPLAINT;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.DIAGNOSIS;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.DIASTOLIC;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.HEIGHT;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.HISTORY_OF_PRESENT_ILLNESS;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.INVESTIGATION_RESULT;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.OXYGEN_SATURATION;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.PATIENT_HISTORY;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.PHYSICAL_EXAMINATION;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.PULSE;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.RESPIRATORY_RATE;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.SYSTOLIC;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.TEMPERATURE;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.TREATMENT_PLAN;
import static com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationRequiredField.WEIGHT;

/**
 * NPHIES pre-authorization claim types and the fields each one makes mandatory
 * (GapSheet 3.2.1, "MDS IMPORTANT"). Every type needs a diagnosis.
 */
public enum PreAuthorizationClaimType {

    /** Professional - OP: clinical text and every vital sign. */
    PROFESSIONAL_OP("professional", "op", clinicalWithVitals()),
    /**
     * Professional - Emergency: the same fields as OP. Still sent with sub type "op":
     * "emr" also needs the emergency encounter details, which the request does not carry yet.
     */
    PROFESSIONAL_EMERGENCY("professional", "op", clinicalWithVitals()),
    /** Dental - OP: clinical text and chief complaint, no vital signs; sent as "oral" / "op". */
    ORAL_OP("oral", "op", dental());

    /** NPHIES encounter class for a dental claim ("Allowed code = AMB only"). */
    public static final String ORAL_ENCOUNTER_CLASS = "AMB";

    private final String nphiesType;
    private final String nphiesSubType;
    private final Set<PreAuthorizationRequiredField> requiredFields;

    PreAuthorizationClaimType(String nphiesType, String nphiesSubType, Set<PreAuthorizationRequiredField> requiredFields) {
        this.nphiesType = nphiesType;
        this.nphiesSubType = nphiesSubType;
        this.requiredFields = Collections.unmodifiableSet(requiredFields);
    }

    /** {@code preAuthorizationInfo.type} sent to Waseel. */
    public String nphiesType() {
        return nphiesType;
    }

    /** {@code preAuthorizationInfo.subType} sent to Waseel. */
    public String nphiesSubType() {
        return nphiesSubType;
    }

    public boolean isOral() {
        return this == ORAL_OP;
    }

    public Set<PreAuthorizationRequiredField> requiredFields() {
        return requiredFields;
    }

    private static EnumSet<PreAuthorizationRequiredField> dental() {
        return EnumSet.of(
                DIAGNOSIS,
                CHIEF_COMPLAINT,
                TREATMENT_PLAN,
                PATIENT_HISTORY,
                HISTORY_OF_PRESENT_ILLNESS,
                PHYSICAL_EXAMINATION,
                INVESTIGATION_RESULT
        );
    }

    private static EnumSet<PreAuthorizationRequiredField> clinicalWithVitals() {
        EnumSet<PreAuthorizationRequiredField> fields = dental();
        fields.addAll(EnumSet.of(
                TEMPERATURE,
                PULSE,
                OXYGEN_SATURATION,
                RESPIRATORY_RATE,
                SYSTOLIC,
                DIASTOLIC,
                HEIGHT,
                WEIGHT
        ));
        return fields;
    }
}

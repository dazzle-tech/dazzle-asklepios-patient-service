package com.dazzle.asklepios.domain.enumeration;

public enum PatientActivityType {
    APPOINTMENT_SCHEDULED(PatientActivityCategory.APPOINTMENT_COORDINATION),
    APPOINTMENT_CONFIRMED(PatientActivityCategory.APPOINTMENT_COORDINATION),
    APPOINTMENT_RESCHEDULED(PatientActivityCategory.APPOINTMENT_COORDINATION),
    APPOINTMENT_CANCELLED(PatientActivityCategory.APPOINTMENT_COORDINATION),
    NO_SHOW_FOLLOW_UP(PatientActivityCategory.APPOINTMENT_COORDINATION),
    PATIENT_CHECK_IN_FOLLOW_UP(PatientActivityCategory.APPOINTMENT_COORDINATION),
    REFERRAL_COORDINATION(PatientActivityCategory.APPOINTMENT_COORDINATION),
    FOLLOW_UP_APPOINTMENT_REMINDER(PatientActivityCategory.APPOINTMENT_COORDINATION),

    REVIEW_CLINICAL_DOCUMENTATION(PatientActivityCategory.CLINICAL_FOLLOW_UP),
    REVIEW_DIAGNOSIS_OR_TREATMENT_PLAN(PatientActivityCategory.CLINICAL_FOLLOW_UP),
    FOLLOW_UP_ON_SYMPTOMS(PatientActivityCategory.CLINICAL_FOLLOW_UP),
    MEDICATION_RELATED_FOLLOW_UP(PatientActivityCategory.CLINICAL_FOLLOW_UP),
    REVIEW_TEST_RESULTS(PatientActivityCategory.CLINICAL_FOLLOW_UP),
    FOLLOW_UP_ON_ABNORMAL_RESULTS(PatientActivityCategory.CLINICAL_FOLLOW_UP),
    SPECIALIST_CONSULTATION_COORDINATION(PatientActivityCategory.CLINICAL_FOLLOW_UP),
    POST_VISIT_FOLLOW_UP(PatientActivityCategory.CLINICAL_FOLLOW_UP),

    LAB_TEST_PENDING(PatientActivityCategory.LABORATORY_RADIOLOGY),
    LAB_RESULT_FOLLOW_UP(PatientActivityCategory.LABORATORY_RADIOLOGY),
    RADIOLOGY_APPOINTMENT_COORDINATION(PatientActivityCategory.LABORATORY_RADIOLOGY),
    IMAGING_RESULT_FOLLOW_UP(PatientActivityCategory.LABORATORY_RADIOLOGY),
    MISSING_RESULT_INVESTIGATION(PatientActivityCategory.LABORATORY_RADIOLOGY),
    CRITICAL_RESULT_ESCALATION(PatientActivityCategory.LABORATORY_RADIOLOGY),
    REPEAT_TEST_COORDINATION(PatientActivityCategory.LABORATORY_RADIOLOGY),

    INSURANCE_ELIGIBILITY_VERIFICATION(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),
    PRIOR_AUTHORIZATION_REQUEST(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),
    AUTHORIZATION_FOLLOW_UP(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),
    MISSING_DOCUMENTATION_FOR_CLAIM(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),
    MEDICAL_CODING_CLARIFICATION(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),
    CLAIM_SUBMISSION_FOLLOW_UP(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),
    CLAIM_REJECTION_RESOLUTION(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),
    PATIENT_PAYMENT_FOLLOW_UP(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),
    REFUND_OR_BILLING_INQUIRY(PatientActivityCategory.INSURANCE_REVENUE_CYCLE),

    PHONE_CALL(PatientActivityCategory.PATIENT_COMMUNICATION),
    SMS_OR_MESSAGING(PatientActivityCategory.PATIENT_COMMUNICATION),
    EMAIL(PatientActivityCategory.PATIENT_COMMUNICATION),
    PATIENT_COMPLAINT(PatientActivityCategory.PATIENT_COMMUNICATION),
    PATIENT_INQUIRY(PatientActivityCategory.PATIENT_COMMUNICATION),
    PATIENT_EDUCATION(PatientActivityCategory.PATIENT_COMMUNICATION),
    APPOINTMENT_REMINDER_SENT(PatientActivityCategory.PATIENT_COMMUNICATION),
    UNABLE_TO_REACH_PATIENT(PatientActivityCategory.PATIENT_COMMUNICATION),

    DOCUMENT_REQUESTED(PatientActivityCategory.ADMINISTRATIVE),
    DOCUMENT_RECEIVED(PatientActivityCategory.ADMINISTRATIVE),
    MISSING_INFORMATION(PatientActivityCategory.ADMINISTRATIVE),
    DATA_CORRECTION(PatientActivityCategory.ADMINISTRATIVE),
    INTERNAL_DEPARTMENT_COORDINATION(PatientActivityCategory.ADMINISTRATIVE),
    EXTERNAL_PROVIDER_COORDINATION(PatientActivityCategory.ADMINISTRATIVE),
    PATIENT_RECORD_REVIEW(PatientActivityCategory.ADMINISTRATIVE),
    GENERAL_TASK_OR_FOLLOW_UP(PatientActivityCategory.ADMINISTRATIVE);

    private final PatientActivityCategory category;

    PatientActivityType(PatientActivityCategory category) {
        this.category = category;
    }

    public PatientActivityCategory getCategory() {
        return category;
    }

    public boolean belongsTo(PatientActivityCategory category) {
        return this.category == category;
    }

    public PatientActivityStatus defaultStatus() {
        return switch (this) {
            case APPOINTMENT_SCHEDULED,
                    APPOINTMENT_CONFIRMED,
                    APPOINTMENT_RESCHEDULED,
                    APPOINTMENT_CANCELLED,
                    PHONE_CALL,
                    SMS_OR_MESSAGING,
                    EMAIL,
                    PATIENT_COMPLAINT,
                    PATIENT_INQUIRY,
                    PATIENT_EDUCATION,
                    APPOINTMENT_REMINDER_SENT,
                    UNABLE_TO_REACH_PATIENT,
                    DOCUMENT_RECEIVED -> PatientActivityStatus.DONE;
            default -> PatientActivityStatus.OPEN;
        };
    }

    public PatientActivityPriority defaultPriority() {
        if (this == CRITICAL_RESULT_ESCALATION) {
            return PatientActivityPriority.HIGH;
        }
        return PatientActivityPriority.NORMAL;
    }
}

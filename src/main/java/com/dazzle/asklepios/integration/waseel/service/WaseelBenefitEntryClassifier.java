package com.dazzle.asklepios.integration.waseel.service;

import java.util.Locale;

/**
 * Classifies Waseel eligibility benefit entries into three distinct financial concepts:
 * <ul>
 *   <li>Patient copayment (% and per-service maximum) — beneficiary share</li>
 *   <li>Maximum benefit — insurance coverage cap</li>
 *   <li>Approval limit — pre-authorization cap on insurance share</li>
 * </ul>
 */
final class WaseelBenefitEntryClassifier {

    private WaseelBenefitEntryClassifier() {
    }

    enum EntryType {
        PATIENT_COPAYMENT_PERCENT,
        PATIENT_COPAYMENT_MAXIMUM,
        INSURANCE_MAXIMUM_BENEFIT,
        INSURANCE_APPROVAL_LIMIT,
        UNCLASSIFIED
    }

    static EntryType classify(String typeDisplay, String typeCode) {
        String display = normalize(typeDisplay);
        String code = normalize(typeCode);

        if (isPatientCopaymentPercent(display, code)) {
            return EntryType.PATIENT_COPAYMENT_PERCENT;
        }

        if (isPatientCopaymentMaximum(display, code)) {
            return EntryType.PATIENT_COPAYMENT_MAXIMUM;
        }

        if (isInsuranceApprovalLimit(display, code)) {
            return EntryType.INSURANCE_APPROVAL_LIMIT;
        }

        if (isInsuranceMaximumBenefit(display, code)) {
            return EntryType.INSURANCE_MAXIMUM_BENEFIT;
        }

        return EntryType.UNCLASSIFIED;
    }

    /**
     * Copayment percent per service — patient share rate (e.g. 20%).
     */
    private static boolean isPatientCopaymentPercent(String display, String code) {
        if (display.contains("copayment") || display.contains("co-payment") || code.contains("copay")) {
            return display.contains("percent") || code.contains("percent");
        }
        return display.contains("copayment percent per service")
                || display.equals("copayment percent per service.");
    }

    /**
     * Copayment maximum per service — patient share cap per line (e.g. 75 SAR).
     * This is NOT an insurance coverage limit.
     */
    private static boolean isPatientCopaymentMaximum(String display, String code) {
        if (display.contains("copayment") || display.contains("co-payment") || code.contains("copay")) {
            return display.contains("maximum") && display.contains("service");
        }
        return display.contains("copayment maximum per service")
                || display.equals("copayment maximum per service.");
    }

    /**
     * Insurance-side coverage cap (e.g. Maximum benefit allowable).
     */
    private static boolean isInsuranceMaximumBenefit(String display, String code) {
        if (display.contains("copayment")
                || display.contains("co-payment")
                || display.contains("approval")) {
            return false;
        }

        return display.contains("maximum benefit allowable")
                || display.contains("benefit limit")
                || display.contains("coverage maximum")
                || display.contains("insurance maximum")
                || display.contains("policy maximum")
                || display.contains("annual limit")
                || (display.contains("maximum") && display.contains("benefit") && !display.contains("copayment"));
    }

    /**
     * Pre-authorization limit on insurance responsibility.
     */
    private static boolean isInsuranceApprovalLimit(String display, String code) {
        return display.contains("approval") || code.contains("approval");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

package com.dazzle.asklepios.domain.enumeration.waseelIntegration;

/**
 * Waseel/NPHIES claim {@code subType}. Acceptable values are {@code ip}, {@code op}, {@code emr}.
 * Portal labels map as: OutPatient → {@code op}, Emergency → {@code emr}.
 */
public enum WaseelClaimSubType {
    OUTPATIENT("op"),
    EMERGENCY("emr");

    private final String waseelCode;

    WaseelClaimSubType(String waseelCode) {
        this.waseelCode = waseelCode;
    }

    public String waseelCode() {
        return waseelCode;
    }

    public static WaseelClaimSubType fromWaseelCode(String code) {
        if (code == null || code.isBlank()) {
            return OUTPATIENT;
        }

        String normalized = code.trim().toLowerCase();
        for (WaseelClaimSubType subType : values()) {
            if (subType.waseelCode.equals(normalized) || subType.name().equalsIgnoreCase(normalized)) {
                return subType;
            }
        }

        if ("outpatient".equals(normalized) || "out-patient".equals(normalized)) {
            return OUTPATIENT;
        }
        if ("em".equals(normalized) || "emergency".equals(normalized)) {
            return EMERGENCY;
        }

        return OUTPATIENT;
    }
}

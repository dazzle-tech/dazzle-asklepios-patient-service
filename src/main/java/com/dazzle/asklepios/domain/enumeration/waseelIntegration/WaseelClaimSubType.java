package com.dazzle.asklepios.domain.enumeration.waseelIntegration;

/**
 * Waseel/NPHIES claim {@code subType}. Portal labels map as:
 * OutPatient → {@code op}, Emergency → {@code em}.
 */
public enum WaseelClaimSubType {
    OUTPATIENT("op"),
    EMERGENCY("em");

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

        return OUTPATIENT;
    }
}

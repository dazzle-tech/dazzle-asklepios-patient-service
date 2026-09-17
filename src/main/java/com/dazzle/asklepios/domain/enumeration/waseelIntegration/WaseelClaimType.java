package com.dazzle.asklepios.domain.enumeration.waseelIntegration;

/**
 * Waseel/NPHIES claim {@code type}. Portal labels map as:
 * Professional → {@code professional}, Dental → {@code oral}, Pharmacy → {@code pharmacy}.
 */
public enum WaseelClaimType {
    PROFESSIONAL("professional"),
    DENTAL("oral"),
    PHARMACY("pharmacy");

    private final String waseelCode;

    WaseelClaimType(String waseelCode) {
        this.waseelCode = waseelCode;
    }

    public String waseelCode() {
        return waseelCode;
    }

    public static WaseelClaimType fromWaseelCode(String code) {
        if (code == null || code.isBlank()) {
            return PROFESSIONAL;
        }

        String normalized = code.trim().toLowerCase();
        for (WaseelClaimType type : values()) {
            if (type.waseelCode.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }

        if ("dental".equals(normalized)) {
            return DENTAL;
        }
        if ("medication".equals(normalized) || "medications".equals(normalized)) {
            return PHARMACY;
        }

        return PROFESSIONAL;
    }
}

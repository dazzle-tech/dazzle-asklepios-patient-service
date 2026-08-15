package com.dazzle.asklepios.integration.waseel.service;

import java.util.Locale;

/**
 * Desired mock search outcome for Waseel pre-authorization testing.
 */
public enum WaseelMockPreAuthStatus {
    APPROVED,
    REJECTED,
    PARTIAL,
    PENDED;

    public static WaseelMockPreAuthStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return APPROVED;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "rejected", "reject", "denied" -> REJECTED;
            case "partial", "partially", "partially-approved" -> PARTIAL;
            case "pended", "pending", "queued", "queue" -> PENDED;
            default -> APPROVED;
        };
    }

    public String waseelStatus() {
        return switch (this) {
            case APPROVED -> "approved";
            case REJECTED -> "rejected";
            case PARTIAL -> "partial";
            case PENDED -> "pended";
        };
    }

    public String waseelOutcome() {
        return switch (this) {
            case APPROVED, REJECTED, PARTIAL -> "Processing Complete";
            case PENDED -> "Queued";
        };
    }
}

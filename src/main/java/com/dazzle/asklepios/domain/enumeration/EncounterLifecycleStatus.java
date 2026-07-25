package com.dazzle.asklepios.domain.enumeration;

/**
 * High-level encounter lifecycle used for billing and visit tracking.
 */
public enum EncounterLifecycleStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED,
    CANCELLED;

    public static EncounterLifecycleStatus fromDatabaseValue(String value) {
        if (value == null) {
            return null;
        }
        if ("INPROGRESS".equalsIgnoreCase(value)
                || "IN_PROGRESS".equalsIgnoreCase(value)) {
            return IN_PROGRESS;
        }
        return valueOf(value);
    }
}

package com.dazzle.asklepios.integration.waseel.mock;

import java.util.Locale;

public enum WaseelPreAuthorizationMockScenario {
    APPROVED,
    PARTIAL,
    REJECTED,
    ROTATE;

    public static WaseelPreAuthorizationMockScenario fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return APPROVED;
        }

        try {
            return WaseelPreAuthorizationMockScenario.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return APPROVED;
        }
    }
}

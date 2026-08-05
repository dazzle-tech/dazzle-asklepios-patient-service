package com.dazzle.asklepios.integration.waseel.service.mapper;

import java.util.Locale;

/**
 * Maps internal / setup item-type labels to Waseel NPHIES claim item types.
 */
public final class WaseelItemTypeNormalizer {

    private WaseelItemTypeNormalizer() {}

    public static String normalize(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String normalized = type.trim().toUpperCase(Locale.ROOT).replace('_', '-');

        return switch (normalized) {
            case "SERVICE", "SERVICES", "CONSULTATION" -> "SERVICES";
            case "PROCEDURE", "PROCEDURES" -> "PROCEDURES";
            case "RADIOLOGY" -> "IMAGING";
            case "MEDICATION", "MEDICATIONS", "MEDICATION-CODE" -> "MEDICATION-CODES";
            case "MEDICAL-DEVICE" -> "MEDICAL-DEVICES";
            case "LAB" -> "LABORATORY";
            case "COSMETIC", "COSMETIC-CODE" -> "COSMETIC-CODES";
            case "HERBAL", "HERBAL-AND-VITAMIN", "HERBAL-AND-VITAMIN-CODE" -> "HERBAL-AND-VITAMIN-CODES";
            case "NUTRITION", "NUTRITION-CODE" -> "NUTRITION-CODES";
            case "TRANSPORTATION" -> "TRANSPORTATION-SRCA";
            default -> normalized;
        };
    }
}

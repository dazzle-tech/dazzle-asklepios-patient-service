package com.dazzle.asklepios.integration.nahdi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nahdi integration settings, bound from the {@code nahdi.*} properties (overridable through {@code NAHDI_*} environment variables).
 */
@ConfigurationProperties(prefix = "nahdi")
public record NahdiProperties(
        boolean enabled,
        String baseUrl,
        String providerId,
        String providerToken,
        Integer timeoutSeconds,
        String sendPrescriptionEndpoint,
        Prescription prescription
) {

    /**
     * Fixed codes sent in the SendPrescription payload.
     */
    public record Prescription(
            String status,
            String typeCash,
            String typeInsurance,
            String itemApprovalStatus,
            // "SFDA" or "NPHIES": the item code sent first; the other one is used when it is missing.
            String itemCodePriority
    ) {}

    @Override
    public String toString() {
        return "NahdiProperties{enabled=" + enabled + ", baseUrl='" + baseUrl + "', providerId='" + providerId
                + "', providerToken='***', timeoutSeconds=" + timeoutSeconds + "}";
    }
}

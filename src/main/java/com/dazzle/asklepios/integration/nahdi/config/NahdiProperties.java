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
        Prescription prescription,
        Dispensing dispensing
) {

    /**
     * Fixed codes sent in the SendPrescription payload.
     */
    public record Prescription(
            String status,
            String typeCash,
            String typeInsurance,
            String itemApprovalStatus,
            // APP_STATUS of a line cancelled after the prescription was sent.
            String itemCancelledStatus,
            // "SFDA" or "NPHIES": the item code sent first; the other one is used when it is missing.
            String itemCodePriority
    ) {}

    /**
     * Dispensing status of a sent prescription, read from Nahdi sales invoices.
     *
     * @param salesInvoicesEndpoint GetSalesInvoices path
     * @param saleInvoiceType       Invoice_Type of a sale (the medications on it were dispensed)
     * @param returnInvoiceType     Invoice_Type of a return (the medications on it are no longer dispensed)
     */
    public record Dispensing(
            String salesInvoicesEndpoint,
            String saleInvoiceType,
            String returnInvoiceType
    ) {}

    @Override
    public String toString() {
        return "NahdiProperties{enabled=" + enabled + ", baseUrl='" + baseUrl + "', providerId='" + providerId
                + "', providerToken='***', timeoutSeconds=" + timeoutSeconds + "}";
    }
}

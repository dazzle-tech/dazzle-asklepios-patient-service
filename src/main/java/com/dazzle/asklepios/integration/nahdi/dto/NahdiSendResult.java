package com.dazzle.asklepios.integration.nahdi.dto;

/**
 * Outcome of sending a prescription to Nahdi.
 *
 * @param status      SUCCESS, FAILED or SKIPPED (integration disabled / not configured)
 * @param orderNumber Nahdi order number on success
 * @param message     for the doctor when the prescription was not accepted (see NahdiDoctorMessage)
 * @param detail      technical reason of a failure (e.g. Nahdi's error), for IT
 */
public record NahdiSendResult(
        String status,
        String orderNumber,
        String message,
        String detail
) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    public static NahdiSendResult success(String orderNumber) {
        return new NahdiSendResult(SUCCESS, orderNumber, null, null);
    }

    public static NahdiSendResult failed(String message, String detail) {
        return new NahdiSendResult(FAILED, null, message, detail);
    }

    public static NahdiSendResult skipped(String message) {
        return new NahdiSendResult(SKIPPED, null, message, null);
    }
}

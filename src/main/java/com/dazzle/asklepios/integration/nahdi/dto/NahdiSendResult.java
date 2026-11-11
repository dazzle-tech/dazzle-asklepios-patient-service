package com.dazzle.asklepios.integration.nahdi.dto;

/**
 * Outcome of sending a prescription to Nahdi.
 *
 * @param status      SUCCESS, FAILED or SKIPPED (integration disabled / not configured)
 * @param orderNumber Nahdi order number on success
 * @param message     reason when the prescription was not accepted
 */
public record NahdiSendResult(
        String status,
        String orderNumber,
        String message
) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    public static NahdiSendResult success(String orderNumber) {
        return new NahdiSendResult(SUCCESS, orderNumber, null);
    }

    public static NahdiSendResult failed(String message) {
        return new NahdiSendResult(FAILED, null, message);
    }

    public static NahdiSendResult skipped(String message) {
        return new NahdiSendResult(SKIPPED, null, message);
    }
}

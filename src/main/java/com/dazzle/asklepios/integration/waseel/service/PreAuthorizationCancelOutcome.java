package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCancelResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reads the outcome of a Waseel pre-authorization cancel. Per the Waseel API, the cancel "outcome" is
 * "cancelled" or "failed", and the search "cancelStatus" is "Failed" or "Error" when the cancel did not go through.
 */
final class PreAuthorizationCancelOutcome {

    static final String CANCELLED_STATUS = "CANCELLED";

    private static final String CANCELLED = "cancelled";
    private static final String NO_REASON = "no reason returned by Waseel";

    private PreAuthorizationCancelOutcome() {
    }

    /**
     * A submission with outcome "error" was stopped by NPHIES validation: no authorization exists at the
     * payer, so there is nothing to cancel there.
     */
    static boolean neverReachedPayer(String submissionOutcome) {
        return submissionOutcome != null && submissionOutcome.toLowerCase(Locale.ROOT).contains("error");
    }

    static boolean cancelledByPayer(PreAuthorizationCancelResponse cancelResponse, PreAuthorizationSearchResponse searchResponse) {
        return (cancelResponse != null && CANCELLED.equalsIgnoreCase(trim(cancelResponse.outcome())))
                || (searchResponse != null && CANCELLED.equalsIgnoreCase(trim(searchResponse.cancelStatus())));
    }

    /** Why the cancel did not go through, e.g. "RE-00133: Task focus is not referring to a valid resource". */
    static String reason(PreAuthorizationCancelResponse cancelResponse, PreAuthorizationSearchResponse searchResponse) {
        String errors = cancelResponse == null ? null : errorText(cancelResponse.errors());
        return firstNonBlank(
                searchResponse == null ? null : searchResponse.cancelResponseReason(),
                cancelResponse == null ? null : cancelResponse.statusReason(),
                errors,
                cancelResponse == null ? null : cancelResponse.message(),
                NO_REASON
        );
    }

    /** NPHIES errors arrive as { coding: [ { code, display } ] }. */
    private static String errorText(List<Object> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        String text = errors.stream()
                .filter(Map.class::isInstance)
                .map(error -> ((Map<?, ?>) error).get("coding"))
                .filter(List.class::isInstance)
                .flatMap(coding -> ((List<?>) coding).stream())
                .filter(Map.class::isInstance)
                .map(coding -> (Map<?, ?>) coding)
                .map(coding -> joinNonBlank(coding.get("code"), coding.get("display")))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("; "));
        return text.isBlank() ? null : text;
    }

    private static String joinNonBlank(Object code, Object display) {
        String codeText = code == null ? null : trim(code.toString());
        String displayText = display == null ? null : trim(display.toString());
        if (codeText == null) {
            return displayText;
        }
        return displayText == null ? codeText : codeText + ": " + displayText;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String text = trim(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}

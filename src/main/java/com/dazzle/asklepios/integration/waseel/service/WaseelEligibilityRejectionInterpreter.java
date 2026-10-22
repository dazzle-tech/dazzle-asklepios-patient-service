package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a Waseel eligibility payload into a user-facing rejection.
 * A completed check that finds coverage, including coverage that is not in force,
 * is not a rejection. A member, policy, or payer that Waseel does not recognize is.
 */
public final class WaseelEligibilityRejectionInterpreter {

    public static final String INSURANCE_NOT_FOUND_KEY = "waseel.eligibility.insuranceNotFound";
    public static final String REJECTED_KEY = "waseel.eligibility.rejected";

    public static final String INSURANCE_NOT_FOUND_MESSAGE =
            "This insurance was not found at Waseel. Check the payer, policy number, and member card ID, then try again.";

    private static final int MAX_DETAIL_LENGTH = 400;

    private WaseelEligibilityRejectionInterpreter() {
    }

    public record Rejection(String message, String errorKey) {
    }

    public static Optional<Rejection> fromResponse(EligibilityResponse response, ObjectMapper objectMapper) {
        if (response == null) {
            return Optional.of(new Rejection(
                    "Waseel returned an empty eligibility response. Try again.",
                    REJECTED_KEY
            ));
        }

        String errorText = joinMessages(readNode(response.errors(), objectMapper));
        String disposition = clean(response.disposition());
        String coverageText = coverageReasons(response);
        String combined = join(join(disposition, errorText), coverageText);

        if (!isRejected(response.outcome(), response.status(), errorText, combined)) {
            return Optional.empty();
        }

        if (isInsuranceNotFound(combined)) {
            return Optional.of(new Rejection(INSURANCE_NOT_FOUND_MESSAGE, INSURANCE_NOT_FOUND_KEY));
        }

        return Optional.of(new Rejection(
                rejectedMessage(firstSpecific(firstSpecific(errorText, coverageText), disposition)),
                REJECTED_KEY
        ));
    }

    public static Optional<Rejection> insuranceNotFoundFromHttpBody(String responseBody, ObjectMapper objectMapper) {
        String described = describeHttpError(responseBody, objectMapper);
        if (!isInsuranceNotFound(described)) {
            return Optional.empty();
        }

        return Optional.of(new Rejection(INSURANCE_NOT_FOUND_MESSAGE, INSURANCE_NOT_FOUND_KEY));
    }

    public static String describeHttpError(String responseBody, ObjectMapper objectMapper) {
        if (responseBody == null || responseBody.isBlank() || objectMapper == null) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(responseBody);
            String described = joinMessages(node);
            if (described != null) {
                return described;
            }
        } catch (Exception ignored) {
            // Fall back to the raw body when Waseel did not return JSON.
        }

        String trimmed = responseBody.trim();
        if (trimmed.isEmpty() || isGeneric(trimmed)) {
            return null;
        }

        return limit(trimmed);
    }

    private static boolean isRejected(String outcome, String status, String errorText, String combined) {
        return isFailedToken(outcome)
                || isFailedToken(status)
                || errorText != null
                || isInsuranceNotFound(combined);
    }

    private static boolean isFailedToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "error", "failed", "failure", "rejected" -> true;
            default -> false;
        };
    }

    static boolean isInsuranceNotFound(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("not found")
                || normalized.contains("does not exist")
                || normalized.contains("doesn't exist")
                || normalized.contains("do not exist")
                || normalized.contains("not exist")
                || normalized.contains("no beneficiary")
                || normalized.contains("unknown payer")
                || normalized.contains("unknown member")
                || normalized.contains("invalid payer")
                || normalized.contains("invalid member")
                || normalized.contains("not registered")
                || normalized.contains("no such")
                || normalized.contains("could not find")
                || normalized.contains("could not be found")
                || normalized.contains("cannot find")
                || normalized.contains("cannot be found")
                || normalized.contains("no matching coverage")
                || normalized.contains("no matching member")
                || normalized.contains("no matching payer")
                || normalized.contains("no matching policy")
                || normalized.contains("member id does not")
                || normalized.contains("no eligibility");
    }

    private static String rejectedMessage(String detail) {
        if (detail == null) {
            return "Waseel could not verify this insurance. Check the payer, policy number, and member card ID, then try again.";
        }

        return "Waseel could not verify this insurance. " + detail;
    }

    private static String firstSpecific(String preferred, String fallback) {
        if (preferred != null && !isGeneric(preferred)) {
            return preferred;
        }
        if (fallback != null && !isGeneric(fallback)) {
            return fallback;
        }
        return null;
    }

    private static String coverageReasons(EligibilityResponse response) {
        if (response.coverages() == null || response.coverages().isEmpty()) {
            return null;
        }

        List<String> reasons = new ArrayList<>();
        response.coverages().forEach(coverage -> {
            if (coverage == null) {
                return;
            }
            String reason = clean(coverage.notInforceReason());
            if (reason != null && !reasons.contains(reason)) {
                reasons.add(reason);
            }
        });

        return reasons.isEmpty() ? null : String.join(" ", reasons);
    }

    private static JsonNode readNode(Object value, ObjectMapper objectMapper) {
        if (value == null || objectMapper == null) {
            return null;
        }

        return objectMapper.valueToTree(value);
    }

    private static String joinMessages(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        Set<String> messages = new LinkedHashSet<>();
        collectMessages(node, messages, true);
        messages.removeIf(WaseelEligibilityRejectionInterpreter::isGeneric);
        if (messages.isEmpty()) {
            return null;
        }

        return limit(String.join(" ", messages));
    }

    private static void collectMessages(JsonNode node, Set<String> messages, boolean topLevel) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }

        if (node.isTextual()) {
            addMessage(messages, node.asText());
            return;
        }

        if (node.isArray()) {
            node.forEach(child -> collectMessages(child, messages, false));
            return;
        }

        if (!node.isObject()) {
            return;
        }

        String message = firstText(
                node,
                "message",
                "errorMessage",
                "errorDescription",
                "detail",
                "display",
                "error"
        );
        String description = firstText(node, "description");
        String code = firstText(node, "code", "errorCode");
        if (message != null && !isGeneric(message)) {
            addMessage(messages, code == null || topLevel ? message : code + ": " + message);
        }
        if (description != null
                && !description.equalsIgnoreCase(message)
                && !isGeneric(description)) {
            addMessage(messages, description);
        }

        collectIfPresent(node.get("errors"), messages);
        collectIfPresent(node.get("errorList"), messages);
        collectIfPresent(node.get("validationErrors"), messages);

        JsonNode error = node.get("error");
        if (error != null && !error.isTextual()) {
            collectMessages(error, messages, false);
        }
    }

    private static void collectIfPresent(JsonNode node, Set<String> messages) {
        if (node != null && !node.isNull()) {
            collectMessages(node, messages, false);
        }
    }

    private static void addMessage(Set<String> messages, String value) {
        String cleaned = clean(value);
        if (cleaned != null) {
            messages.add(cleaned);
        }
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && child.isTextual()) {
                String value = clean(child.asText());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String join(String left, String right) {
        List<String> parts = new ArrayList<>();
        if (left != null) {
            parts.add(left);
        }
        if (right != null && (left == null || !left.equalsIgnoreCase(right))) {
            parts.add(right);
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static boolean isGeneric(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "bad request", "error", "errors", "failed", "failure", "ok", "internal server error" -> true;
            default -> false;
        };
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String limit(String value) {
        if (value.length() <= MAX_DETAIL_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_DETAIL_LENGTH) + "...";
    }
}

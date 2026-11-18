package com.dazzle.asklepios.integration.nahdi.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Readable reason of a Nahdi rejection ({@code success=false}).
 * <p>
 * Nahdi answers HTTP 200 with {@code "message":""} and the reasons in {@code error}, e.g.
 * {@code [{"ErrorCode":"Invalid Provider ID or Token","ErrorDescription":"ER01- Invalid Provider ID or Token"}]};
 * {@code error} can also be a plain string or empty.
 */
public final class NahdiErrorReason {

    static final String NO_REASON = "Nahdi rejected the request without a reason";

    private NahdiErrorReason() {}

    /** {@code message} when filled, otherwise each error's description (or code), joined with "; ". */
    public static String of(String message, JsonNode error) {
        if (StringUtils.hasText(message)) {
            return message.trim();
        }
        List<String> reasons = new ArrayList<>();
        if (error != null && error.isArray()) {
            error.forEach(item -> add(reasons, item));
        } else {
            add(reasons, error);
        }
        return reasons.isEmpty() ? NO_REASON : String.join("; ", reasons);
    }

    private static void add(List<String> reasons, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        String text;
        if (node.isObject()) {
            text = firstText(node, "ErrorDescription", "ErrorCode", "message", "Message");
            if (text == null && !node.isEmpty()) {
                text = node.toString();
            }
        } else {
            text = node.asText();
        }
        if (StringUtils.hasText(text)) {
            reasons.add(text.trim());
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }
}

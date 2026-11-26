package com.dazzle.asklepios.integration.nahdi.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the doctor is told when a prescription did not reach Nahdi. The technical reason (e.g. an invalid token)
 * is kept in the integration log and shown as a hint only; the doctor sees either a general message (technical
 * errors, nothing for them to fix) or a clear reason they can act on (errors in the prescription data).
 */
public final class NahdiDoctorMessage {

    public static final String TECHNICAL =
            "Could not reach the pharmacy system. Try Resend later; if it keeps failing, contact IT.";

    /**
     * Nahdi error codes caused by the prescription data (the code before "-" in ErrorDescription, e.g. "ER01"),
     * with the message shown to the doctor. Empty until Nahdi shares its error list: every other error,
     * including ER01 (invalid provider/token) and ER0Date (date format), is technical.
     */
    static final Map<String, String> DATA_ERRORS = Map.of();

    private static final Pattern ERROR_CODE = Pattern.compile("^\s*([A-Za-z0-9]+)\s*-");

    /**
     * Nahdi order statuses in which it skips an update, with the message shown to the doctor. Nahdi then answers
     * e.g. "Order Stratus is 1, Order cannot changed" (its spelling), sometimes with {@code success=true}.
     */
    static final Map<String, String> LOCKED_ORDER_STATUSES = Map.of(
            "1", "The pharmacy is already processing this prescription, so Nahdi did not apply the changes. "
                    + "Contact the pharmacy if the changes are needed.",
            "9", "The pharmacy already dispensed this prescription, so Nahdi did not apply the changes. "
                    + "Contact Nahdi if the copay needs to be updated."
    );

    private static final Pattern ORDER_STATUS = Pattern.compile("(?i)order\\s+st\\w*\\s+is\\s+(\\d+)");

    /**
     * @param dataError true when the doctor can fix it in the prescription
     * @param text      message for the doctor
     */
    public record Message(boolean dataError, String text) {}

    private static final Message TECHNICAL_MESSAGE = new Message(false, TECHNICAL);

    private NahdiDoctorMessage() {}

    /** For a failure without a Nahdi answer (timeout, HTTP error, request we could not build). */
    public static Message technical() {
        return TECHNICAL_MESSAGE;
    }

    /** For a Nahdi rejection ({@code success=false}): the data messages of its error codes, otherwise technical. */
    public static Message ofNahdiError(JsonNode error) {
        Set<String> messages = new LinkedHashSet<>();
        for (String code : codes(error)) {
            String message = DATA_ERRORS.get(code);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages.isEmpty() ? TECHNICAL_MESSAGE : new Message(true, String.join(" ", messages));
    }

    /**
     * The message for an order Nahdi would not change (status 1 or 9), read from its reason text (message or
     * error descriptions). Empty for any other answer.
     */
    public static Optional<Message> ofLockedOrder(String reason) {
        if (!StringUtils.hasText(reason)) {
            return Optional.empty();
        }
        Matcher matcher = ORDER_STATUS.matcher(reason);
        while (matcher.find()) {
            String text = LOCKED_ORDER_STATUSES.get(matcher.group(1));
            if (text != null) {
                return Optional.of(new Message(false, text));
            }
        }
        return Optional.empty();
    }

    /** Error codes of a Nahdi {@code error} value, e.g. "ER01" from "ER01- Invalid Provider ID or Token". */
    static List<String> codes(JsonNode error) {
        List<String> codes = new ArrayList<>();
        if (error == null || !error.isArray()) {
            return codes;
        }
        for (JsonNode item : error) {
            JsonNode description = item == null ? null : item.get("ErrorDescription");
            String text = description == null || description.isNull() ? null : description.asText();
            if (StringUtils.hasText(text)) {
                Matcher matcher = ERROR_CODE.matcher(text);
                if (matcher.find()) {
                    codes.add(matcher.group(1).toUpperCase());
                }
            }
        }
        return codes;
    }
}

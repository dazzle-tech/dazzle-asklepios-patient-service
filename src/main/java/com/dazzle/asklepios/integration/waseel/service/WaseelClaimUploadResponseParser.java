package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimUploadResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Waseel claim-upload payloads are not a stable Jackson mapping: dates, numeric
 * coercions, wrappers, and even JSON arrays all appear in the wild. Parse from
 * JsonNode so a single mismatched field cannot fail the whole upload.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WaseelClaimUploadResponseParser {

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    private static final DateTimeFormatter OFFSET_WITHOUT_COLON = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .appendPattern("Z")
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd['T'][ ]HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private final ObjectMapper objectMapper;

    public WaseelClaimUploadResponse parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new RestClientException(
                    "Waseel claim upload returned non-JSON body: " + abbreviate(body),
                    ex
            );
        }

        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }

        if (root.isTextual()) {
            String nested = root.asText();
            if (nested == null || nested.isBlank()) {
                return null;
            }
            return parse(nested);
        }

        if (root.isArray()) {
            if (root.isEmpty()) {
                return null;
            }
            JsonNode first = root.get(0);
            if (first != null && first.isObject()) {
                root = first;
            } else if (first != null && first.isNumber()) {
                List<Long> ids = new ArrayList<>();
                root.forEach(node -> {
                    if (node != null && node.isNumber()) {
                        ids.add(node.longValue());
                    }
                });
                throw new RestClientException(
                        "Waseel returned existing upload IDs instead of an upload summary: " + ids
                );
            } else {
                throw new RestClientException(
                        "Waseel claim upload returned a JSON array that is not an upload summary: "
                                + abbreviate(body)
                );
            }
        }

        if (!root.isObject()) {
            throw new RestClientException(
                    "Waseel claim upload returned JSON that is not an object: " + abbreviate(body)
            );
        }

        JsonNode payload = unwrap(root);
        WaseelClaimUploadResponse parsed = fromNode(payload);
        if (parsed.uploadId() == null && parsed.noOfUploadedClaims() == null && parsed.message() == null) {
            String waseelError = firstText(
                    payload,
                    "error",
                    "errorMessage",
                    "message",
                    "detail",
                    "description"
            );
            if (waseelError != null) {
                log.warn("[CLAIM_UPLOAD] Parsed empty upload summary. Waseel message={}", waseelError);
            }
        }
        return parsed;
    }

    private JsonNode unwrap(JsonNode root) {
        for (String wrapper : List.of("data", "result", "uploadSummary", "body", "payload")) {
            JsonNode nested = root.get(wrapper);
            if (nested != null && nested.isObject() && looksLikeUploadSummary(nested)) {
                return nested;
            }
        }
        return root;
    }

    private boolean looksLikeUploadSummary(JsonNode node) {
        return node.has("uploadId")
                || node.has("uploadSummaryID")
                || node.has("uploadSummaryId")
                || node.has("uploadName")
                || node.has("noOfUploadedClaims");
    }

    private WaseelClaimUploadResponse fromNode(JsonNode node) {
        return new WaseelClaimUploadResponse(
                asLong(node, "transcationLogId", "transactionLogId", "transactionlogId"),
                asMessage(node),
                asLong(node, "uploadId", "uploadSummaryID", "uploadSummaryId"),
                asLong(node, "providerId"),
                asText(node, "uploadName"),
                asOffsetDateTime(node, "uploadDate"),
                asInteger(node, "noOfNotUploadedClaims"),
                asInteger(node, "noOfUploadedClaims"),
                asDecimal(node, "totalAmtOfUploadedClaims", "netAmountOfUploadedClaims"),
                asInteger(node, "noOfAcceptedClaims"),
                asDecimal(node, "totalAmtOfAcceptedClaims", "netAmountOfAcceptedClaims"),
                asInteger(node, "noOfNotAcceptedClaims"),
                asDecimal(node, "totalAmtOfNotAcceptedClaims", "netAmountOfNotAcceptedClaims"),
                asOffsetDateTime(node, "lastModifiedDate"),
                asDecimal(node, "ratioOfAccepted"),
                asDecimal(node, "ratioOfNotAccepted")
        );
    }

    private String asMessage(JsonNode node) {
        JsonNode message = firstPresent(node, "message", "error", "errorMessage", "detail");
        if (message == null || message.isNull()) {
            return null;
        }
        if (message.isTextual() || message.isNumber() || message.isBoolean()) {
            String text = message.asText();
            return text == null || text.isBlank() ? null : text.trim();
        }
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            return message.toString();
        }
    }

    private static JsonNode firstPresent(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                return value;
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... names) {
        JsonNode value = firstPresent(node, names);
        if (value == null) {
            return null;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            String text = value.asText();
            return text == null || text.isBlank() ? null : text.trim();
        }
        return null;
    }

    private static String asText(JsonNode node, String... names) {
        return firstText(node, names);
    }

    private static Long asLong(JsonNode node, String... names) {
        JsonNode value = firstPresent(node, names);
        if (value == null) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        if (value.isTextual()) {
            String text = value.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(text).longValue();
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static Integer asInteger(JsonNode node, String... names) {
        Long value = asLong(node, names);
        return value == null ? null : value.intValue();
    }

    private static BigDecimal asDecimal(JsonNode node, String... names) {
        JsonNode value = firstPresent(node, names);
        if (value == null) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            String text = value.asText().trim().replace("%", "");
            if (text.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static OffsetDateTime asOffsetDateTime(JsonNode node, String... names) {
        JsonNode value = firstPresent(node, names);
        if (value == null) {
            return null;
        }
        if (value.isNumber()) {
            long epoch = value.longValue();
            Instant instant = epoch > 10_000_000_000L
                    ? Instant.ofEpochMilli(epoch)
                    : Instant.ofEpochSecond(epoch);
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (value.isArray() && value.size() >= 3) {
            int year = value.get(0).asInt();
            int month = value.get(1).asInt();
            int day = value.get(2).asInt();
            int hour = value.size() > 3 ? value.get(3).asInt() : 0;
            int minute = value.size() > 4 ? value.get(4).asInt() : 0;
            int second = value.size() > 5 ? value.get(5).asInt() : 0;
            int nano = 0;
            if (value.size() > 6) {
                int fraction = value.get(6).asInt();
                nano = fraction < 1_000_000_000 ? fraction : 0;
            }
            return LocalDateTime.of(year, month, day, hour, minute, second, nano)
                    .atZone(RIYADH)
                    .toOffsetDateTime();
        }
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // try other Waseel formats
        }
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // try other Waseel formats
        }
        try {
            return OffsetDateTime.parse(text, OFFSET_WITHOUT_COLON);
        } catch (DateTimeParseException ignored) {
            // try other Waseel formats
        }
        try {
            return LocalDateTime.parse(text, LOCAL_DATE_TIME).atZone(RIYADH).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            log.debug("[CLAIM_UPLOAD] Ignoring unparseable date value '{}'", text);
            return null;
        }
    }

    private static String abbreviate(String body) {
        String trimmed = body.trim();
        return trimmed.length() <= 800 ? trimmed : trimmed.substring(0, 800) + "...";
    }
}

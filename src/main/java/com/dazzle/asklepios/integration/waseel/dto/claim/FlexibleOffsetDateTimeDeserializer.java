package com.dazzle.asklepios.integration.waseel.dto.claim;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Waseel claim dates arrive as ISO-8601, RFC-822 offsets ({@code +0000}),
 * epoch millis, or Jackson timestamp arrays.
 */
public class FlexibleOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private static final ZoneOffset WASEEL_OFFSET = ZoneOffset.ofHours(3);

    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter LOCAL_SPACE_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter LOCAL_SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<DateTimeFormatter> OFFSET_FORMATTERS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                    .toFormatter(),
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                    .toFormatter()
    );

    private static final List<DateTimeFormatter> LOCAL_FORMATTERS = List.of(
            LOCAL_DATE_TIME,
            LOCAL_SPACE_MILLIS,
            LOCAL_SPACE
    );

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser == null || parser.currentToken() == null) {
            return null;
        }

        return switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> fromEpoch(parser.getLongValue());
            case VALUE_STRING -> parseText(parser.getText());
            case START_ARRAY -> fromArray(parser);
            default -> null;
        };
    }

    private static OffsetDateTime parseText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        for (DateTimeFormatter formatter : OFFSET_FORMATTERS) {
            try {
                return OffsetDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        for (DateTimeFormatter formatter : LOCAL_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter).atOffset(WASEEL_OFFSET);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        try {
            return fromEpoch(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static OffsetDateTime parse(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return fromEpoch(node.longValue());
        }
        if (node.isTextual()) {
            return parseText(node.asText());
        }
        return null;
    }

    private static OffsetDateTime fromEpoch(long value) {
        Instant instant = value > 10_000_000_000L
                ? Instant.ofEpochMilli(value)
                : Instant.ofEpochSecond(value);
        return OffsetDateTime.ofInstant(instant, WASEEL_OFFSET);
    }

    private static OffsetDateTime fromArray(JsonParser parser) throws IOException {
        Integer year = nextInt(parser);
        Integer month = nextInt(parser);
        Integer day = nextInt(parser);
        Integer hour = nextInt(parser);
        Integer minute = nextInt(parser);
        Integer second = nextInt(parser);
        skipRestOfArray(parser);

        if (year == null || month == null || day == null) {
            return null;
        }
        LocalDateTime local = LocalDateTime.of(
                year,
                month,
                day,
                hour == null ? 0 : hour,
                minute == null ? 0 : minute,
                second == null ? 0 : second
        );
        return local.atOffset(WASEEL_OFFSET);
    }

    private static Integer nextInt(JsonParser parser) throws IOException {
        JsonToken token = parser.nextToken();
        if (token == null || token == JsonToken.END_ARRAY || token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token.isNumeric()) {
            return parser.getIntValue();
        }
        return null;
    }

    private static void skipRestOfArray(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        while (token != null && token != JsonToken.END_ARRAY) {
            token = parser.nextToken();
        }
    }
}

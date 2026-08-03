package com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Accepts numeric or string long values from Waseel search payloads.
 */
public class FlexibleLongDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser == null || parser.currentToken() == null) {
            return null;
        }

        return switch (parser.currentToken()) {
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getLongValue();
            case VALUE_STRING -> {
                String text = parser.getText();
                if (text == null || text.isBlank()) {
                    yield null;
                }
                try {
                    yield Long.valueOf(text.trim());
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            case VALUE_NULL -> null;
            default -> null;
        };
    }
}
